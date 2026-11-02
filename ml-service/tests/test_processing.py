from datetime import date, timedelta

import pytest

from app import params
from app.processing import (
    BaselineBin,
    Observation,
    RainComparison,
    RainDay,
    StatusLimits,
    anomaly_score,
    apply_pixel_limit,
    build_baseline,
    complete_total,
    dap_bin,
    days_after_planting,
    ndvi,
    one_per_day,
    past_season_starts,
    plot_status,
    rain_percent,
    same_day_in_year,
    smooth,
)

DAY = date(2025, 10, 1)


def obs(day_offset: int, value: float | None, pixels: int = 100) -> Observation:
    return Observation(DAY + timedelta(days=day_offset), value, pixels)


def test_ndvi_formula():
    assert ndvi(0.5, 0.1) == pytest.approx(0.6667, abs=1e-4)
    assert ndvi(0.1, 0.5) == pytest.approx(-0.6667, abs=1e-4)
    assert ndvi(0.3, 0.3) == 0


def test_ndvi_of_no_signal_is_no_data_not_zero():
    assert ndvi(0, 0) is None


def test_pixel_limit_blanks_dates_below_it_and_keeps_their_pixel_count():
    kept, blanked = apply_pixel_limit([obs(0, 0.5, 20), obs(5, 0.5, 19)], 20)
    assert kept.usable
    assert not blanked.usable
    assert blanked.clear_pixels == 19


def test_a_date_with_no_value_is_not_usable():
    assert not obs(0, None, 500).usable


def test_duplicate_date_keeps_the_clearer_image_and_sorts():
    first = obs(5, 0.4, 50)
    clearer = obs(5, 0.6, 200)
    earlier = obs(0, 0.3, 100)
    assert one_per_day([first, clearer, earlier]) == [earlier, clearer]


def test_smoothing_removes_a_single_bad_image():
    series = [obs(0, 0.60), obs(5, 0.61), obs(10, 0.10), obs(15, 0.62), obs(20, 0.63)]
    smoothed = smooth(series)
    assert smoothed[DAY + timedelta(days=10)] == pytest.approx(0.61)


def test_smoothing_skips_unusable_dates():
    series = [obs(0, 0.5), obs(5, None, 0), obs(10, None, 3), obs(15, 0.6)]
    smoothed = smooth(series)
    assert set(smoothed) == {DAY, DAY + timedelta(days=15)}


def test_smoothing_with_no_usable_data_is_empty():
    assert smooth([obs(0, None, 0)]) == {}
    assert smooth([]) == {}


def test_days_after_planting_and_bins():
    assert days_after_planting(date(2025, 10, 1), date(2025, 9, 20)) == 11
    assert dap_bin(0) == 0
    assert dap_bin(9) == 0
    assert dap_bin(10) == 10
    assert dap_bin(57) == 50


def test_leap_day_maps_to_28_february():
    assert same_day_in_year(date(2024, 2, 29), 2023) == date(2023, 2, 28)


def test_past_seasons_start_in_2019_and_stop_before_the_current_year():
    starts = past_season_starts(date(2022, 9, 20))
    assert starts == [date(2019, 9, 20), date(2020, 9, 20), date(2021, 9, 20)]


def test_past_seasons_are_empty_for_the_first_year():
    assert past_season_starts(date(2019, 9, 20)) == []


def season(planting: date, dap: int, value: float, pixels: int = 100) -> list[Observation]:
    return [Observation(planting + timedelta(days=dap), value, pixels)]


def test_baseline_percentiles_use_known_values():
    seasons = {
        date(2019 + i, 9, 20): season(date(2019 + i, 9, 20), 12, value)
        for i, value in enumerate([0.1, 0.2, 0.3, 0.4, 0.5])
    }
    result = build_baseline(seasons)
    assert len(result) == 1
    assert result[0].dap_bin == 10
    assert result[0].p50 == pytest.approx(0.3)
    assert result[0].p10 == pytest.approx(0.14)
    assert result[0].p90 == pytest.approx(0.46)


def test_baseline_leaves_out_bins_with_too_few_samples():
    seasons = {
        date(2019 + i, 9, 20): season(date(2019 + i, 9, 20), 12, 0.3)
        for i in range(params.BASELINE_MIN_SAMPLES - 1)
    }
    assert build_baseline(seasons) == []


def test_baseline_ignores_cloudy_dates_and_dates_outside_the_season():
    planting = date(2020, 9, 20)
    seasons = {
        planting: [
            Observation(planting - timedelta(days=5), 0.9, 100),
            Observation(planting + timedelta(days=params.BASELINE_MAX_DAP + 5), 0.9, 100),
            Observation(planting + timedelta(days=12), None, 1),
        ]
    }
    assert build_baseline(seasons) == []


def test_baseline_of_no_data_is_empty():
    assert build_baseline({}) == []


PLANTED = date(2024, 9, 1)
LIMITS = StatusLimits(min_clear_pixels=20, window_days=20, min_observations=1, watch_rain_pct=60, watch_anomaly=-1,
                      at_risk_anomaly=-1.5, consecutive_observations=2, min_days=10)
BANDS = [BaselineBin(dap, p10=0.4, p50=0.6, p90=0.8) for dap in range(0, 100, 10)]
LATE_DAY = date(2024, 10, 30)
NORMAL_RAIN = RainComparison(LATE_DAY, 100, 100, 100)
LOW_RAIN = RainComparison(LATE_DAY, 40, 100, 40)

# With BANDS, NDVI 0.4 is an anomaly of -1, 0.39 of -1.05, 0.3 of -1.5 and 0.29 of -1.55.


def status_of(readings, rain=NORMAL_RAIN, bins=BANDS, as_of=LATE_DAY, limits=LIMITS):
    """readings are (days before as_of, NDVI), oldest first."""
    observations = [Observation(as_of - timedelta(days=ago), value, 100) for ago, value in readings]
    smoothed = {o.day: o.ndvi for o in observations}
    return plot_status(observations, smoothed, bins, PLANTED, as_of, rain, limits)


def test_anomaly_formula():
    assert anomaly_score(0.2, BaselineBin(0, 0.4, 0.6, 0.8)) == pytest.approx(-2)


def test_anomaly_with_a_flat_band_is_unknown_not_zero():
    assert anomaly_score(0.2, BaselineBin(0, 0.6, 0.6, 0.8)) is None


def test_a_flat_band_gives_not_enough_data():
    result = status_of([(5, 0.1)], bins=[BaselineBin(dap, 0.6, 0.6, 0.8) for dap in range(0, 100, 10)])
    assert result.status == "NOT_ENOUGH_DATA"


def test_anomaly_of_exactly_minus_one_is_healthy():
    result = status_of([(1, 0.4)])
    assert result.status == "HEALTHY"
    assert result.cause is None


def test_anomaly_just_below_minus_one_is_watch():
    assert status_of([(1, 0.39)]).status == "WATCH"


def test_low_rain_alone_is_watch_with_no_cause():
    result = status_of([(5, 0.7)], rain=LOW_RAIN)
    assert result.status == "WATCH"
    assert result.cause is None
    assert result.signals == [{"code": "RAIN_BELOW_NORMAL", "rainPct": 40}]


def test_rain_at_exactly_the_limit_is_not_low():
    assert status_of([(5, 0.7)], rain=RainComparison(LATE_DAY, 60, 100, 60)).status == "HEALTHY"


def test_two_readings_ten_days_apart_at_the_limit_are_at_risk():
    assert status_of([(11, 0.3), (1, 0.3)]).status == "AT_RISK"


def test_one_severe_reading_is_only_watch():
    assert status_of([(1, 0.1)]).status == "WATCH"


def test_two_severe_readings_under_ten_days_apart_are_only_watch():
    # Question 17: both conditions must hold.
    assert status_of([(10, 0.1), (1, 0.1)]).status == "WATCH"


def test_a_long_span_with_one_reading_in_the_run_is_only_watch():
    assert status_of([(30, 0.7), (1, 0.1)]).status == "WATCH"


def test_readings_below_the_band_but_not_severe_stay_watch():
    # Question 16: -1.05 is WATCH, because AT_RISK needs -1.5.
    assert status_of([(20, 0.39), (10, 0.39), (1, 0.39)]).status == "WATCH"


def test_a_recovery_breaks_the_severe_run():
    assert status_of([(20, 0.1), (11, 0.7), (1, 0.1)]).status == "WATCH"


def test_a_cloudy_gap_does_not_break_a_run():
    observations = [Observation(LATE_DAY - timedelta(days=11), 0.1, 100),
                    Observation(LATE_DAY - timedelta(days=6), None, 3),
                    Observation(LATE_DAY - timedelta(days=1), 0.1, 100)]
    smoothed = {o.day: o.ndvi for o in observations if o.usable}
    assert plot_status(observations, smoothed, BANDS, PLANTED, LATE_DAY, NORMAL_RAIN, LIMITS).status == "AT_RISK"


def test_severe_readings_can_start_before_the_window():
    assert status_of([(25, 0.1), (5, 0.1)]).status == "AT_RISK"


def test_low_ndvi_with_low_rain_is_water_deficit():
    result = status_of([(11, 0.1), (1, 0.1)], rain=LOW_RAIN)
    assert result.status == "AT_RISK"
    assert result.cause == "WATER_DEFICIT"
    assert result.needs_field_visit is False
    assert result.confidence == "MEDIUM"


def test_low_ndvi_with_normal_rain_is_unexplained_and_asks_for_a_visit():
    result = status_of([(11, 0.1), (1, 0.1)])
    assert result.cause == "UNEXPLAINED"
    assert result.needs_field_visit is True
    assert result.confidence == "LOW"


def test_unknown_rain_is_not_treated_as_low_rain():
    result = status_of([(11, 0.1), (1, 0.1)], rain=None)
    assert result.cause == "UNEXPLAINED"
    assert result.rain_pct is None
    assert {"code": "RAIN_NOT_COMPARED"} in result.signals
    assert result.confidence == "LOW"


def test_confidence_never_reaches_high_before_back_testing():
    readings = [(ago, 0.7) for ago in range(14, 0, -2)]
    assert status_of(readings).confidence == "MEDIUM"


def test_a_reading_on_the_last_day_of_the_window_counts():
    assert status_of([(19, 0.7)]).status == "HEALTHY"


def test_a_reading_one_day_before_the_window_does_not_count():
    result = status_of([(20, 0.7)])
    assert result.status == "NOT_ENOUGH_DATA"
    assert result.confidence == "LOW"
    assert result.signals == [{"code": "NO_RECENT_READINGS"}]


def test_too_few_readings_for_the_limit_is_not_enough_data():
    limits = StatusLimits(20, 20, 2, 60, -1, -1.5, 2, 10)
    assert status_of([(5, 0.7)], limits=limits).status == "NOT_ENOUGH_DATA"


def test_no_normal_curve_for_this_stage_is_not_enough_data():
    result = status_of([(5, 0.1)], bins=[])
    assert result.status == "NOT_ENOUGH_DATA"
    assert result.signals == [{"code": "NO_NORMAL_CURVE"}]


def test_low_rain_never_replaces_missing_readings():
    assert status_of([], rain=LOW_RAIN).status == "NOT_ENOUGH_DATA"


def test_readings_after_as_of_are_ignored():
    assert status_of([(-5, 0.1)]).status == "NOT_ENOUGH_DATA"


def test_causes_never_name_a_pest_or_disease():
    causes = {status_of([(11, 0.1), (1, 0.1)], rain=rain).cause for rain in (NORMAL_RAIN, LOW_RAIN, None)}
    assert causes == {"WATER_DEFICIT", "UNEXPLAINED"}


def test_complete_total_needs_every_day():
    days = [RainDay(date(2024, 1, 1), 1.0), RainDay(date(2024, 1, 2), 0.0)]
    assert complete_total(days, date(2024, 1, 1), date(2024, 1, 2)) == 1
    assert complete_total(days, date(2024, 1, 1), date(2024, 1, 3)) is None


def test_rain_percent_needs_enough_past_seasons():
    assert rain_percent(50, [100, 100], LATE_DAY) is None
    assert rain_percent(50, [100, 100, 100], LATE_DAY).pct_of_normal == 50


def test_rain_percent_with_no_normal_rain_is_unknown():
    assert rain_percent(0, [0, 0, 0], LATE_DAY) is None


def test_missing_threshold_is_refused():
    with pytest.raises(ValueError, match="watch_rain_pct"):
        StatusLimits.from_thresholds({"watch_rain_pct": None})
