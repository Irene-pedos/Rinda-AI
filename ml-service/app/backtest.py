"""Replays the status rules through past seasons on the demo plots, to see how often each result appears.

Run from ml-service, with the backend running: .venv\\Scripts\\python -m app.backtest
It reads the thresholds table, saves nothing, and prints Markdown for docs/ML_VALIDATION.md.

This counts results. It can't measure accuracy, because there is no field ground truth yet.
"""

import sys
from collections import Counter
from dataclasses import dataclass
from datetime import date, timedelta

from app import backend_client, gee_source, params, pipeline
from app.config import get_settings
from app.processing import (
    Observation,
    PlotStatus,
    RainDay,
    StatusLimits,
    apply_pixel_limit,
    build_baseline,
    one_per_day,
    past_season_starts,
    plot_status,
    smooth,
)
from app.sample_source import SampleSource

STEP_DAYS = 5
SEASON_DAYS = params.BASELINE_MAX_DAP
RAIN_LEAD_DAYS = 90
SEASON_YEARS = (2023, 2024, 2025)
FIRST_YEAR_LOADED = params.BASELINE_FIRST_YEAR
LAST_YEAR_LOADED = max(SEASON_YEARS)
STATUSES = ("HEALTHY", "WATCH", "AT_RISK", "NOT_ENOUGH_DATA")

# An assumption for this test only. The demo plots have no confirmed planting date (question 1).
ASSUMED_PLANTING_MONTH_DAY = (9, 20)

# The polygons from db/demo/R__demo_seed.sql. They are placeholder locations, not known maize fields.
DEMO_PLOTS = {
    9001: [[30.1000, -1.9500], [30.1020, -1.9500], [30.1020, -1.9480], [30.1000, -1.9480], [30.1000, -1.9500]],
    9002: [[30.1030, -1.9500], [30.1050, -1.9500], [30.1050, -1.9480], [30.1030, -1.9480], [30.1030, -1.9500]],
    9003: [[30.1060, -1.9500], [30.1080, -1.9500], [30.1080, -1.9480], [30.1060, -1.9480], [30.1060, -1.9500]],
    9004: [[30.1090, -1.9500], [30.1110, -1.9500], [30.1110, -1.9480], [30.1090, -1.9480], [30.1090, -1.9500]],
}

# One setting changes at a time, so each row shows what that setting does.
VARIATIONS = {
    "Team settings": {},
    "`at_risk_anomaly` -1.25": {"at_risk_anomaly": -1.25},
    "`at_risk_anomaly` -2": {"at_risk_anomaly": -2},
    "`at_risk_min_days` 5": {"at_risk_min_days": 5},
    "`at_risk_min_days` 15": {"at_risk_min_days": 15},
    "`not_enough_data_window_days` 15": {"not_enough_data_window_days": 15},
    "`not_enough_data_window_days` 30": {"not_enough_data_window_days": 30},
    "`min_clear_pixels` 100": {"min_clear_pixels": 100},
}


class CachedSource:
    """Loads each season once, so replaying many dates doesn't repeat Earth Engine calls."""

    def __init__(self, source, years: range):
        self.origin = source.origin
        self._observations: list[Observation] = []
        self._rain: list[RainDay] = []
        month, day = ASSUMED_PLANTING_MONTH_DAY
        for year in years:
            start = date(year, month, day)
            self._observations += source.observations(start, start + timedelta(days=SEASON_DAYS))
            self._rain += source.rain(start - timedelta(days=RAIN_LEAD_DAYS), start + timedelta(days=SEASON_DAYS))

    def observations(self, start: date, end: date) -> list[Observation]:
        return [obs for obs in self._observations if start <= obs.day <= end]

    def rain(self, start: date, end: date) -> list[RainDay]:
        return [day for day in self._rain if start <= day.day <= end]


@dataclass(frozen=True)
class Result:
    plot: str
    season: int
    as_of: date
    dap: int
    status: PlotStatus


def replay(plot: str, source, planting: date, last_day: date, thresholds: dict) -> list[Result]:
    """The status on each date, using only what was known on that date."""
    limits = StatusLimits.from_thresholds(thresholds)

    def valid(start: date, end: date) -> list[Observation]:
        return apply_pixel_limit(one_per_day(source.observations(start, end)), limits.min_clear_pixels)

    season = valid(planting, last_day)
    bins = build_baseline({
        start: valid(start, start + timedelta(days=SEASON_DAYS)) for start in past_season_starts(planting)
    })

    results = []
    as_of = planting + timedelta(days=STEP_DAYS)
    while as_of <= last_day:
        known = [obs for obs in season if obs.day <= as_of]
        rain = pipeline.compare_rain(pipeline.PlotJob(0, planting, "A", as_of), source)
        status = plot_status(known, smooth(known), bins, planting, as_of, rain, limits)
        results.append(Result(plot, planting.year, as_of, (as_of - planting).days, status))
        as_of += timedelta(days=STEP_DAYS)
    return results


def counts(results: list[Result]) -> Counter:
    return Counter(result.status.status for result in results)


def changes_of_status(results: list[Result]) -> int:
    """How often the status moves between dates. Many changes would mean a jumpy status."""
    ordered = [result.status.status for result in results]
    return sum(1 for before, after in zip(ordered, ordered[1:]) if before != after)


def _percent(part: int, total: int) -> str:
    return f"{part / total * 100:.0f}%" if total else "n/a"


def _count_cells(results: list[Result]) -> str:
    found = counts(results)
    return " | ".join(f"{found[status]} ({_percent(found[status], len(results))})" for status in STATUSES)


def by_season_table(results: list[Result]) -> list[str]:
    lines = [
        "| Plot | Season | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA | Status changes |",
        "|---|---|---|---|---|---|---|---|",
    ]
    keys = sorted({(result.plot, result.season) for result in results})
    for plot, season in keys:
        chosen = [r for r in results if (r.plot, r.season) == (plot, season)]
        lines.append(f"| {plot} | {season} | {len(chosen)} | {_count_cells(chosen)} | {changes_of_status(chosen)} |")
    return lines


def variations_table(load_results) -> list[str]:
    lines = [
        "| Settings | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA |",
        "|---|---|---|---|---|---|",
    ]
    for label, change in VARIATIONS.items():
        results = load_results(change)
        lines.append(f"| {label} | {len(results)} | {_count_cells(results)} |")
    return lines


def causes_table(results: list[Result]) -> list[str]:
    found = Counter((r.status.status, r.status.cause or "none") for r in results
                    if r.status.status in ("WATCH", "AT_RISK"))
    lines = ["| Status | Likely cause | Results |", "|---|---|---|"]
    lines += [f"| {status} | {cause} | {count} |" for (status, cause), count in sorted(found.items())]
    return lines


def not_enough_data_table(results: list[Result]) -> list[str]:
    """Why results were NOT_ENOUGH_DATA, by growth stage, since clouds and a thin baseline need different fixes."""
    found = Counter()
    for result in results:
        if result.status.status == "NOT_ENOUGH_DATA":
            reason = result.status.signals[0]["code"]
            found[(reason, "Up to 30 DAP" if result.dap <= 30 else "After 30 DAP")] += 1
    lines = ["| Reason | Stage | Results |", "|---|---|---|"]
    lines += [f"| {reason} | {stage} | {count} |" for (reason, stage), count in sorted(found.items())]
    return lines


def confidence_table(results: list[Result]) -> list[str]:
    found = Counter(result.status.confidence for result in results)
    lines = ["| Confidence | Results |", "|---|---|"]
    lines += [f"| {level} | {found[level]} ({_percent(found[level], len(results))}) |"
              for level in ("HIGH", "MEDIUM", "LOW")]
    return lines


def estimated_curve_table(results: list[Result]) -> list[str]:
    """How many results rest on a normal band that was estimated from its neighbours."""
    judged = [r for r in results if r.status.status != "NOT_ENOUGH_DATA"]
    estimated = [r for r in judged if any(s["code"] == "NORMAL_CURVE_ESTIMATED" for s in r.status.signals)]
    lines = ["| Results with a status | Using an estimated band |", "|---|---|"]
    lines.append(f"| {len(judged)} | {len(estimated)} ({_percent(len(estimated), len(judged))}) |")
    return lines


def at_risk_table(results: list[Result]) -> list[str]:
    lines = ["| Plot | Season | Date | DAP | Anomaly | Signals |", "|---|---|---|---|---|---|"]
    for result in results:
        if result.status.status == "AT_RISK":
            codes = ", ".join(signal["code"] for signal in result.status.signals)
            lines.append(f"| {result.plot} | {result.season} | {result.as_of} | {result.dap} "
                         f"| {result.status.anomaly:.2f} | {codes} |")
    return lines


def first_detection_table(results: list[Result]) -> list[str]:
    lines = ["| Plot | Season | First WATCH (DAP) | First AT_RISK (DAP) |", "|---|---|---|---|"]
    for plot, season in sorted({(r.plot, r.season) for r in results}):
        chosen = [r for r in results if (r.plot, r.season) == (plot, season)]
        first = {status: next((r.dap for r in chosen if r.status.status == status), "none")
                 for status in ("WATCH", "AT_RISK")}
        lines.append(f"| {plot} | {season} | {first['WATCH']} | {first['AT_RISK']} |")
    return lines


def _simulated(thresholds: dict) -> list[Result]:
    source = SampleSource()
    results = []
    for year in SEASON_YEARS:
        planting = source.planting_date.replace(year=year)
        last_day = source.as_of if year == source.planting_date.year else planting + timedelta(days=SEASON_DAYS)
        results += replay(f"Simulated ({'stressed' if year == 2025 else 'normal'})", source, planting, last_day,
                          thresholds)
    return results


def _real(sources: dict[int, CachedSource], thresholds: dict) -> list[Result]:
    month, day = ASSUMED_PLANTING_MONTH_DAY
    results = []
    for plot_id, source in sources.items():
        for year in SEASON_YEARS:
            planting = date(year, month, day)
            results += replay(str(plot_id), source, planting, planting + timedelta(days=SEASON_DAYS), thresholds)
    return results


def main() -> int:
    settings = get_settings()
    thresholds = backend_client.get_thresholds(settings)
    years = range(FIRST_YEAR_LOADED, LAST_YEAR_LOADED + 1)
    sources = {}
    for plot_id, ring in DEMO_PLOTS.items():
        print(f"Loading plot {plot_id}", file=sys.stderr)
        boundary = {"type": "Polygon", "coordinates": [ring]}
        sources[plot_id] = CachedSource(gee_source.GeeSource(settings.gee_project_id, boundary), years)

    simulated = _simulated(thresholds)
    real = _real(sources, thresholds)

    sections = [
        ("Simulated sample", by_season_table(simulated)),
        ("Simulated sample, how soon stress shows", first_detection_table(simulated)),
        ("Simulated sample, AT_RISK results", at_risk_table(simulated)),
        ("Real data, by plot and season", by_season_table(real)),
        ("Real data, likely causes", causes_table(real)),
        ("Real data, confidence", confidence_table(real)),
        ("Real data, why NOT_ENOUGH_DATA", not_enough_data_table(real)),
        ("Real data, estimated normal bands", estimated_curve_table(real)),
        ("Real data, AT_RISK results", at_risk_table(real)),
        ("Real data, if a setting changes", variations_table(lambda change: _real(sources, {**thresholds, **change}))),
    ]
    for title, lines in sections:
        print(f"### {title}\n")
        print("\n".join(lines))
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
