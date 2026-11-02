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


JOB = pipeline.PlotJob(plot_id=7, planting_date=date(2021, 9, 20), season_type="A", as_of=date(2021, 10, 20))


def test_result_records_how_it_was_made():
    result = pipeline.run(JOB, FakeSource())
    assert result["plotId"] == 7
    assert result["dataOrigin"] == "GEE"
    assert result["modelVersion"] == params.MODEL_VERSION
    assert result["inputFrom"] == "2021-09-20"
    assert result["inputTo"] == "2021-10-20"
    assert result["params"]["minClearPixels"] == params.MIN_CLEAR_PIXELS
    assert result["params"]["baselineSeasons"] == ["2019-09-20", "2020-09-20"]


def test_no_data_gives_empty_rows_and_no_invented_values():
    result = pipeline.run(JOB, FakeSource())
    assert result["observations"] == []
    assert result["weather"] == []
    assert result["baseline"]["bins"] == []


def test_cloudy_date_keeps_its_pixel_count_but_not_its_ndvi():
    source = FakeSource([Observation(date(2021, 10, 1), 0.8, 5, 90.0)])
    row = pipeline.run(JOB, source)["observations"][0]
    assert row["ndvi"] is None
    assert row["smoothedNdvi"] is None
    assert row["clearPixels"] == 5
    assert row["source"] == "S2"


def test_past_seasons_are_requested_for_the_full_season_length():
    source = FakeSource()
    pipeline.run(JOB, source)
    assert (date(2019, 9, 20), date(2019, 9, 20) + timedelta(days=params.BASELINE_MAX_DAP)) in source.requests


def test_missing_rain_is_left_out_not_counted_as_zero():
    source = FakeSource(rain=[RainDay(date(2021, 10, 1), None), RainDay(date(2021, 10, 2), 0.0)])
    assert pipeline.run(JOB, source)["weather"] == [{"date": "2021-10-02", "rainMm": 0.0}]


def test_as_of_before_planting_is_refused():
    job = pipeline.PlotJob(1, date(2021, 9, 20), "A", date(2021, 9, 1))
    with pytest.raises(ValueError):
        pipeline.run(job, FakeSource())


def test_same_input_gives_the_same_result():
    source = FakeSource([Observation(date(2021, 10, 1), 0.5, 100)])
    assert pipeline.run(JOB, source) == pipeline.run(JOB, source)


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
    result = pipeline.run(job, source)
    assert result["dataOrigin"] == ORIGIN == "SAMPLE_FILE"
    assert len(result["observations"]) > 10
    assert len(result["baseline"]["bins"]) > 10
    for row in result["baseline"]["bins"]:
        assert row["p10"] <= row["p50"] <= row["p90"]


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
