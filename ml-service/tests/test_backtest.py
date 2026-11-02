from datetime import date, timedelta

from app import backtest
from app.processing import Observation, PlotStatus, RainDay

THRESHOLDS = {
    "min_clear_pixels": 20, "not_enough_data_window_days": 20, "not_enough_data_min_obs": 1,
    "watch_rain_pct": 60, "watch_anomaly": -1, "at_risk_anomaly": -1.5, "at_risk_consecutive_obs": 2,
    "at_risk_min_days": 10,
}
PLANTING = date(2024, 9, 20)
LAST_DAY = PLANTING + timedelta(days=60)


class FakeSource:
    origin = "SAMPLE_FILE"

    def __init__(self, observations, rain=()):
        self._observations = list(observations)
        self._rain = list(rain)
        self.calls = 0

    def observations(self, start, end):
        self.calls += 1
        return [obs for obs in self._observations if start <= obs.day <= end]

    def rain(self, start, end):
        self.calls += 1
        return [day for day in self._rain if start <= day.day <= end]


def past_seasons():
    """Five normal seasons, with NDVI spread around 0.6 so the band has width."""
    rows = []
    for year in range(2019, 2024):
        start = date(year, 9, 20)
        rows += [Observation(start + timedelta(days=dap), 0.5 + (year - 2019) * 0.05, 100)
                 for dap in range(0, 151, 5)]
    return rows


def this_season(drop_from_dap):
    return [Observation(PLANTING + timedelta(days=dap), 0.1 if dap >= drop_from_dap else 0.65, 100)
            for dap in range(0, 61, 5)]


def replay(observations):
    return backtest.replay("plot", FakeSource(observations), PLANTING, LAST_DAY, THRESHOLDS)


def test_replay_gives_one_result_per_step():
    results = replay(past_seasons() + this_season(100))
    assert [r.dap for r in results] == list(range(5, 61, 5))


def test_a_later_drop_never_changes_an_earlier_result():
    without = replay(past_seasons() + this_season(100))
    with_drop = replay(past_seasons() + this_season(40))
    early = [r.status.status for r in without if r.dap < 40]
    assert early == [r.status.status for r in with_drop if r.dap < 40]


def test_a_lasting_drop_reaches_at_risk():
    statuses = [r.status.status for r in replay(past_seasons() + this_season(30))]
    assert "AT_RISK" in statuses


def test_the_season_being_tested_is_not_in_its_own_baseline():
    # Only this season's drop exists after 2023, so a baseline using it would hide the drop.
    results = replay(past_seasons() + this_season(0))
    assert all(r.status.status in {"WATCH", "AT_RISK"} for r in results if r.dap >= 20)


def test_no_data_gives_only_not_enough_data():
    assert {r.status.status for r in replay([])} == {"NOT_ENOUGH_DATA"}


def test_counts_and_changes_of_status():
    def result(status):
        return backtest.Result("plot", 2024, PLANTING, 5, PlotStatus(status, "LOW"))

    results = [result("HEALTHY"), result("WATCH"), result("WATCH"), result("HEALTHY")]
    assert backtest.counts(results) == {"HEALTHY": 2, "WATCH": 2}
    assert backtest.changes_of_status(results) == 2


def test_tables_show_every_status_with_shares():
    def result(status):
        return backtest.Result("9001", 2024, PLANTING, 5, PlotStatus(status, "LOW"))

    lines = backtest.by_season_table([result("HEALTHY"), result("WATCH")])
    assert lines[2] == "| 9001 | 2024 | 2 | 1 (50%) | 1 (50%) | 0 (0%) | 0 (0%) | 1 |"


def test_cached_source_loads_each_year_once():
    rain = [RainDay(date(2023, 9, 1), 1.0)]
    inner = FakeSource(past_seasons(), rain)
    cached = backtest.CachedSource(inner, range(2022, 2024))
    calls = inner.calls
    cached.observations(date(2023, 9, 20), date(2023, 10, 1))
    cached.rain(date(2023, 9, 1), date(2023, 9, 2))
    assert inner.calls == calls == 4
    assert cached.rain(date(2023, 9, 1), date(2023, 9, 2)) == rain


def test_estimated_bands_are_counted_among_results_with_a_status():
    def result(status, signals):
        return backtest.Result("9001", 2024, PLANTING, 40, PlotStatus(status, "LOW", signals=signals))

    lines = backtest.estimated_curve_table([
        result("HEALTHY", [{"code": "NORMAL_CURVE_ESTIMATED"}]), result("HEALTHY", []),
        result("NOT_ENOUGH_DATA", [{"code": "NO_NORMAL_CURVE"}])])
    assert lines[2] == "| 2 | 1 (50%) |"


def test_not_enough_data_is_split_by_reason_and_stage():
    def result(code, dap):
        return backtest.Result("9001", 2024, PLANTING, dap,
                               PlotStatus("NOT_ENOUGH_DATA", "LOW", signals=[{"code": code}]))

    lines = backtest.not_enough_data_table(
        [result("NO_NORMAL_CURVE", 10), result("NO_NORMAL_CURVE", 15), result("NO_RECENT_READINGS", 60)])
    assert "| NO_NORMAL_CURVE | Up to 30 DAP | 2 |" in lines
    assert "| NO_RECENT_READINGS | After 30 DAP | 1 |" in lines


def test_first_detection_says_none_when_a_status_never_appears():
    results = [backtest.Result("9001", 2024, PLANTING, 40, PlotStatus("WATCH", "LOW"))]
    assert backtest.first_detection_table(results)[2] == "| 9001 | 2024 | 40 | none |"
