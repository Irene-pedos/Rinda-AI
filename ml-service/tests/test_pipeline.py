import json
from datetime import date, timedelta

import pytest

from app import params, pipeline
from app.gee_source import parse_rain, parse_s2
from app.processing import Observation, RainDay
from app.sample_source import ORIGIN, SAMPLE_FILE, SampleSource


class FakeSource:
    origin = "GEE"

    def __init__(self, observations=(), rain=()):
        self._observations = list(observations)
        self._rain = list(rain)
        self.requests = []

    def observations(self, start, end):
        self.requests.append((start, end))
        return [o for o in self._observations if start <= o.day <= end]

    def rain(self, start, end):
        return [r for r in self._rain if start <= r.day <= end]


THRESHOLDS = {
    "min_clear_pixels": 20, "not_enough_data_window_days": 20, "not_enough_data_min_obs": 1,
    "watch_rain_pct": 60, "watch_anomaly": -1, "at_risk_anomaly": -1.5, "at_risk_consecutive_obs": 2,
    "at_risk_min_days": 10,
}
JOB = pipeline.PlotJob(plot_id=7, planting_date=date(2021, 9, 20), season_type="A", as_of=date(2021, 10, 20))


def test_result_records_how_it_was_made():
    result = pipeline.run(JOB, FakeSource(), THRESHOLDS)
    assert result["plotId"] == 7
    assert result["dataOrigin"] == "GEE"
    assert result["modelVersion"] == params.MODEL_VERSION
    assert result["inputFrom"] == "2021-09-20"
    assert result["inputTo"] == "2021-10-20"
    assert result["params"]["minClearPixels"] == THRESHOLDS["min_clear_pixels"]
    assert result["params"]["baselineSeasons"] == ["2019-09-20", "2020-09-20"]


def test_no_data_gives_empty_rows_and_no_invented_values():
    result = pipeline.run(JOB, FakeSource(), THRESHOLDS)
    assert result["observations"] == []
    assert result["weather"] == []
    assert result["baseline"]["bins"] == []


def test_cloudy_date_keeps_its_pixel_count_but_not_its_ndvi():
    source = FakeSource([Observation(date(2021, 10, 1), 0.8, 5, 90.0)])
    row = pipeline.run(JOB, source, THRESHOLDS)["observations"][0]
    assert row["ndvi"] is None
    assert row["smoothedNdvi"] is None
    assert row["clearPixels"] == 5
    assert row["source"] == "S2"


def test_past_seasons_are_requested_for_the_full_season_length():
    source = FakeSource()
    pipeline.run(JOB, source, THRESHOLDS)
    assert (date(2019, 9, 20), date(2019, 9, 20) + timedelta(days=params.BASELINE_MAX_DAP)) in source.requests


def test_missing_rain_is_left_out_not_counted_as_zero():
    source = FakeSource(rain=[RainDay(date(2021, 10, 1), None), RainDay(date(2021, 10, 2), 0.0)])
    assert pipeline.run(JOB, source, THRESHOLDS)["weather"] == [{"date": "2021-10-02", "rainMm": 0.0}]


def test_as_of_before_planting_is_refused():
    job = pipeline.PlotJob(1, date(2021, 9, 20), "A", date(2021, 9, 1))
    with pytest.raises(ValueError):
        pipeline.run(job, FakeSource(), THRESHOLDS)


def test_same_input_gives_the_same_result():
    source = FakeSource([Observation(date(2021, 10, 1), 0.5, 100)])
    assert pipeline.run(JOB, source, THRESHOLDS) == pipeline.run(JOB, source, THRESHOLDS)


def test_sample_file_says_it_is_simulated():
    data = json.loads(SAMPLE_FILE.read_text(encoding="utf-8"))
    assert data["is_simulated"] is True
    assert "SIMULATED" in data["notice"]


def test_unmarked_file_is_refused(tmp_path):
    path = tmp_path / "plain.json"
    path.write_text(json.dumps({"planting_date": "2025-01-01"}), encoding="utf-8")
    with pytest.raises(ValueError):
        SampleSource(path)


def test_sample_run_is_labelled_and_builds_a_baseline():
    source = SampleSource()
    job = pipeline.PlotJob(9001, source.planting_date, source.season_type, source.as_of)
    result = pipeline.run(job, source, THRESHOLDS)
    assert result["dataOrigin"] == ORIGIN == "SAMPLE_FILE"
    assert len(result["observations"]) > 10
    assert len(result["baseline"]["bins"]) > 10
    for row in result["baseline"]["bins"]:
        assert row["p10"] <= row["p50"] <= row["p90"]
        assert isinstance(row["filled"], bool)


def test_gee_response_parsing():
    features = [
        {"properties": {"date": "2025-01-03", "ndvi": 0.61, "clear_pixels": 180, "cloud_pct": 4.2}},
        {"properties": {"date": "2025-01-08", "clear_pixels": 0, "cloud_pct": 96.0}},
        {"properties": {"date": "2025-01-13"}},
    ]
    parsed = parse_s2(features)
    assert parsed[0] == Observation(date(2025, 1, 3), 0.61, 180, 4.2)
    assert parsed[1].ndvi is None and not parsed[1].usable
    assert parsed[2].clear_pixels == 0

    assert parse_rain([{"properties": {"date": "2025-01-03", "rain_mm": 2.5}},
                       {"properties": {"date": "2025-01-04"}}]) == [
        RainDay(date(2025, 1, 3), 2.5), RainDay(date(2025, 1, 4), None)]


def _rain_days(first, count, mm):
    return [RainDay(first + timedelta(days=i), mm) for i in range(count)]


RAIN_JOB = pipeline.PlotJob(1, date(2024, 9, 1), "A", date(2024, 10, 30))
WINDOW_START = date(2024, 10, 1)


def _past_rain(mm):
    return [day for year in range(2019, 2024) for day in _rain_days(date(year, 10, 1), 30, mm)]


def test_rain_is_compared_with_the_same_dates_in_past_years():
    source = FakeSource(rain=_rain_days(WINDOW_START, 30, 1.0) + _past_rain(2.0))
    comparison = pipeline.compare_rain(RAIN_JOB, source)
    assert comparison.total_mm == 30
    assert comparison.normal_mm == 60
    assert comparison.pct_of_normal == 50


def test_rain_window_ends_on_the_latest_day_chirps_has():
    source = FakeSource(rain=_rain_days(WINDOW_START, 30, 1.0) + _past_rain(1.0))
    late = pipeline.PlotJob(1, date(2024, 9, 1), "A", date(2024, 11, 15))
    assert pipeline.compare_rain(late, source).window_end == date(2024, 10, 30)


def test_a_gap_in_rain_data_means_no_comparison_not_a_dry_day():
    days = [d for d in _rain_days(WINDOW_START, 30, 1.0) if d.day != date(2024, 10, 15)]
    assert pipeline.compare_rain(RAIN_JOB, FakeSource(rain=days + _past_rain(1.0))) is None


def test_too_few_past_seasons_means_no_rain_comparison():
    past = _rain_days(date(2023, 10, 1), 30, 1.0)
    assert pipeline.compare_rain(RAIN_JOB, FakeSource(rain=_rain_days(WINDOW_START, 30, 1.0) + past)) is None


def test_no_rain_data_means_no_rain_comparison():
    assert pipeline.compare_rain(RAIN_JOB, FakeSource()) is None


def test_no_data_gives_not_enough_data_and_never_a_guess():
    status = pipeline.run(JOB, FakeSource(), THRESHOLDS)["status"]
    assert status["status"] == "NOT_ENOUGH_DATA"
    assert status["confidence"] == "LOW"
    assert status["cause"] is None
    assert status["explanation"]["dataQualityNote"]


def test_sample_stress_shows_up_as_at_risk():
    source = SampleSource()
    job = pipeline.PlotJob(9001, source.planting_date, source.season_type, source.as_of)
    status = pipeline.run(job, source, THRESHOLDS)["status"]
    assert status["status"] == "AT_RISK"
    assert status["anomaly"] <= -1.5


def test_sample_season_shows_a_rain_deficit_as_the_likely_cause():
    source = SampleSource()
    job = pipeline.PlotJob(9001, source.planting_date, source.season_type, source.as_of)
    status = pipeline.run(job, source, THRESHOLDS)["status"]
    assert status["rainPct"] < 60
    assert status["cause"] == "WATER_DEFICIT"
    assert status["confidence"] == "MEDIUM"


def test_a_rain_window_crossing_new_year_is_never_compared_with_itself():
    job = pipeline.PlotJob(1, date(2025, 9, 20), "A", date(2026, 1, 10))
    window_start = date(2025, 12, 12)
    past = [day for year in range(2021, 2025) for day in _rain_days(date(year, 12, 12), 30, 1.0)]
    source = FakeSource(rain=_rain_days(window_start, 30, 5.0) + past)
    comparison = pipeline.compare_rain(job, source)
    assert comparison.normal_mm == 30
    assert comparison.pct_of_normal == 500


def test_the_pixel_limit_comes_from_the_thresholds():
    source = FakeSource([Observation(date(2021, 10, 1), 0.5, 25)])
    assert pipeline.run(JOB, source, THRESHOLDS)["observations"][0]["ndvi"] == 0.5
    stricter = {**THRESHOLDS, "min_clear_pixels": 30}
    assert pipeline.run(JOB, source, stricter)["observations"][0]["ndvi"] is None


def test_status_stores_the_thresholds_it_used():
    used = pipeline.run(JOB, FakeSource(), THRESHOLDS)["status"]["thresholdsUsed"]
    assert used == THRESHOLDS
