"""The maize season check and the satellite fetch, on invented NDVI series (no Earth Engine, no backend)."""

from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from app import gee_source, main, params, pipeline
from app.config import Settings, get_settings
from app.gee_source import parse_s2
from app.processing import Observation, RainDay, season_check

PLANTING = date(2024, 9, 20)
PIXELS = 50
MIN_PIXELS = 20


def series(*points: tuple[int, float | None], pixels: int = PIXELS) -> list[Observation]:
    """(days after planting, NDVI) pairs."""
    return [Observation(PLANTING + timedelta(days=dap), ndvi, pixels) for dap, ndvi in points]


MAIZE = series((5, 0.18), (15, 0.22), (25, 0.28), (40, 0.42), (55, 0.62), (70, 0.74), (85, 0.71),
               (100, 0.55), (120, 0.38), (140, 0.3))


def check(observations, days_now, min_pixels=MIN_PIXELS):
    return season_check(observations, PLANTING, PLANTING + timedelta(days=days_now), min_pixels)


# ===== The flag =====

def test_a_full_maize_season_is_consistent():
    result = check(MAIZE, 145)
    assert result.flag == "CONSISTENT"
    assert result.reason == "SEASON_PATTERN_SEEN"
    assert result.signals == ["BARE_START_SEEN", "GROWTH_SEEN", "DECLINE_SEEN"]


def test_bare_start_then_rise_is_consistent_so_far():
    result = check(MAIZE[:6], 72)
    assert result.flag == "CONSISTENT"
    assert result.reason == "CONSISTENT_SO_FAR"
    assert "DECLINE_SEEN" not in result.signals


def test_bare_all_season_is_inconsistent():
    bare = series((5, 0.15), (20, 0.18), (45, 0.2), (75, 0.22), (90, 0.2))
    result = check(bare, 95)
    assert (result.flag, result.reason) == ("INCONSISTENT", "NO_GROWTH_SEEN")


def test_green_cover_at_planting_is_inconsistent():
    green = series((3, 0.72), (12, 0.7), (25, 0.75), (40, 0.74))
    assert (check(green, 45).flag, check(green, 45).reason) == ("INCONSISTENT", "GREEN_AT_PLANTING")


def test_green_long_after_the_season_is_inconsistent():
    perennial = series((5, 0.3), (60, 0.7), (100, 0.72), (155, 0.71), (165, 0.7))
    assert check(perennial, 170).reason == "STILL_GREEN_AFTER_SEASON"


def test_one_green_image_at_planting_decides_nothing():
    one = series((10, 0.75), (40, 0.5))
    assert check(one, 45).flag == "NOT_ENOUGH_DATA"


def test_too_early_in_the_season_is_not_enough_data():
    result = check(series((5, 0.2), (20, 0.25)), 25)
    assert (result.flag, result.reason) == ("NOT_ENOUGH_DATA", "TOO_EARLY")


def test_no_clear_image_after_planting_cant_check_the_start():
    result = check(series((50, 0.6), (65, 0.7), (80, 0.72)), 85)
    assert (result.flag, result.reason) == ("NOT_ENOUGH_DATA", "NO_CLEAR_IMAGE_AFTER_PLANTING")


def test_images_with_too_few_clear_pixels_are_not_counted():
    small = series(*[(dap, ndvi) for dap, ndvi in [(5, 0.2), (15, 0.2), (55, 0.7), (70, 0.7)]], pixels=8)
    result = check(small, 75)
    assert (result.flag, result.reason, result.images_used) == ("NOT_ENOUGH_DATA", "FEW_CLEAR_IMAGES", 0)
    assert result.latest_image_date is None


def test_an_empty_minimum_pixel_count_gives_not_enough_data():
    result = check(MAIZE, 145, min_pixels=None)
    assert (result.flag, result.reason) == ("NOT_ENOUGH_DATA", "MIN_CLEAR_PIXELS_NOT_SET")


def test_images_before_planting_are_ignored():
    before = [Observation(PLANTING - timedelta(days=10), 0.8, PIXELS)] + MAIZE
    assert check(before, 145).flag == "CONSISTENT"


def test_confidence_is_never_high_and_small_plots_stay_low():
    assert check(MAIZE, 145).confidence == "MEDIUM"
    small_plot = series(*[((o.day - PLANTING).days, o.ndvi) for o in MAIZE], pixels=25)
    assert check(small_plot, 145).confidence == "LOW"


def test_the_result_says_which_image_and_how_many_pixels():
    result = check(MAIZE, 145)
    assert result.latest_image_date == PLANTING + timedelta(days=140)
    assert result.pixels_used == PIXELS
    assert result.images_used == len(MAIZE)
    assert "not proof of crop" in result.data_quality_note


# ===== Fetching =====

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


def test_refresh_fetches_only_the_new_window_but_checks_the_whole_season():
    stored, new = MAIZE[:5], MAIZE[5:7]
    source = FakeSource(new)
    job = pipeline.MonitorJob(3, PLANTING, PLANTING + timedelta(days=60), PLANTING + timedelta(days=90),
                              MIN_PIXELS, tuple(stored))
    result = pipeline.monitor(job, source)
    assert source.requests == [(PLANTING + timedelta(days=60), PLANTING + timedelta(days=90))]
    assert [row["date"] for row in result["observations"]] == [o.day.isoformat() for o in new]
    assert result["seasonCheck"]["flag"] == "CONSISTENT"
    assert result["seasonCheck"]["imagesUsed"] == 7


def test_raw_values_are_returned_without_the_pixel_limit():
    cloudy = Observation(PLANTING + timedelta(days=4), 0.31, 3, 88.0, 0.94)
    job = pipeline.MonitorJob(3, PLANTING, PLANTING, PLANTING + timedelta(days=10), MIN_PIXELS)
    row = pipeline.monitor(job, FakeSource([cloudy]))["observations"][0]
    assert row == {"date": cloudy.day.isoformat(), "source": "S2", "ndviMedian": 0.31, "pixelCount": 3,
                   "cloudFraction": 0.94, "sceneCloudPct": 88.0}


def test_the_result_records_how_it_was_made():
    rain = [RainDay(PLANTING, 4.2), RainDay(PLANTING + timedelta(days=1), None)]
    job = pipeline.MonitorJob(3, PLANTING, PLANTING, PLANTING + timedelta(days=10), MIN_PIXELS)
    result = pipeline.monitor(job, FakeSource(rain=rain))
    assert result["modelVersion"] == params.MODEL_VERSION
    assert result["params"]["ndviReducer"] == "median"
    assert result["params"]["minClearPixels"] == MIN_PIXELS
    assert result["rainfall"] == [{"date": PLANTING.isoformat(), "rainMm": 4.2, "source": "CHIRPS"}]


def test_window_ending_before_it_starts_is_refused():
    job = pipeline.MonitorJob(3, PLANTING, PLANTING + timedelta(days=5), PLANTING, MIN_PIXELS)
    with pytest.raises(ValueError):
        pipeline.monitor(job, FakeSource())


def test_cloud_fraction_comes_from_the_plots_own_pixels():
    feature = {"properties": {"date": "2024-10-01", "ndvi": 0.4, "clear_pixels": 30, "total_pixels": 40,
                              "cloud_pct": 12.0}}
    assert parse_s2([feature])[0].cloud_fraction == 0.25
    empty = {"properties": {"date": "2024-10-01", "ndvi": None, "clear_pixels": 0, "total_pixels": 0}}
    assert parse_s2([empty])[0].cloud_fraction is None


# ===== Endpoint =====

KEY = "test-key"
HEADERS = {"X-Internal-Api-Key": KEY}
BOUNDARY = {"type": "Polygon", "coordinates": [[[30.1, -1.95], [30.102, -1.95], [30.102, -1.948], [30.1, -1.95]]]}


@pytest.fixture
def client():
    main.app.dependency_overrides[get_settings] = lambda: Settings("http://backend.test", KEY, "test-project")
    yield TestClient(main.app)
    main.app.dependency_overrides.clear()


def test_monitor_needs_the_api_key(client):
    assert client.post("/monitor/plot", json={"plotId": 1, "source": "sample"}).status_code == 401


def test_monitor_on_the_sample_uses_the_simulated_file(client):
    body = client.post("/monitor/plot", json={"plotId": 9001, "source": "sample", "minClearPixels": 20},
                       headers=HEADERS).json()
    assert body["dataOrigin"] == "SAMPLE_FILE"
    assert body["plantingDate"] == "2025-09-20"
    assert body["observations"]
    assert body["seasonCheck"]["flag"] in ("CONSISTENT", "INCONSISTENT", "NOT_ENOUGH_DATA")


def test_monitor_on_gee_needs_the_boundary_and_planting_date(client):
    response = client.post("/monitor/plot", json={"plotId": 1, "source": "gee"}, headers=HEADERS)
    assert response.status_code == 422


def test_monitor_never_falls_back_to_the_sample(client, monkeypatch):
    def refuse(project_id):
        raise gee_source.GeeNotAuthorised("no credentials")

    monkeypatch.setattr(gee_source, "initialise", refuse)
    response = client.post("/monitor/plot", json={"plotId": 1, "source": "gee", "boundary": BOUNDARY,
                                                  "plantingDate": "2026-09-20"}, headers=HEADERS)
    assert response.status_code == 503


def test_monitor_passes_history_and_window_to_the_source(client, monkeypatch):
    fake = FakeSource(MAIZE[5:7])
    monkeypatch.setattr(gee_source, "GeeSource", lambda project, boundary: fake)
    history = [{"date": o.day.isoformat(), "ndviMedian": o.ndvi, "pixelCount": o.clear_pixels} for o in MAIZE[:5]]
    body = client.post("/monitor/plot", json={
        "plotId": 4, "source": "gee", "boundary": BOUNDARY, "plantingDate": PLANTING.isoformat(),
        "fetchFrom": (PLANTING + timedelta(days=60)).isoformat(), "asOf": (PLANTING + timedelta(days=90)).isoformat(),
        "minClearPixels": MIN_PIXELS, "history": history}, headers=HEADERS).json()
    assert fake.requests == [(PLANTING + timedelta(days=60), PLANTING + timedelta(days=90))]
    assert body["seasonCheck"]["flag"] == "CONSISTENT"
