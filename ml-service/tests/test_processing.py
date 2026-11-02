from datetime import date, timedelta

import pytest

from app import params
from app.processing import (
    Observation,
    build_baseline,
    dap_bin,
    days_after_planting,
    ndvi,
    one_per_day,
    past_season_starts,
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


def test_usable_needs_a_value_and_enough_clear_pixels():
    assert obs(0, 0.5, params.MIN_CLEAR_PIXELS).usable
    assert not obs(0, 0.5, params.MIN_CLEAR_PIXELS - 1).usable
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
    series = [obs(0, 0.5), obs(5, None, 0), obs(10, 0.7, 3), obs(15, 0.6)]
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
            Observation(planting + timedelta(days=12), 0.9, 1),
        ]
    }
    assert build_baseline(seasons) == []


def test_baseline_of_no_data_is_empty():
    assert build_baseline({}) == []
