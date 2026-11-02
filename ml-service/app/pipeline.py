"""Turns a data source into the result the backend stores."""

from dataclasses import dataclass
from datetime import date, timedelta
from typing import Protocol

from app import params
from app.processing import (
    Observation,
    PlotStatus,
    RainComparison,
    RainDay,
    StatusLimits,
    apply_pixel_limit,
    build_baseline,
    complete_total,
    one_per_day,
    past_season_starts,
    plot_status,
    rain_percent,
    same_day_in_year,
    smooth,
)


class DataSource(Protocol):
    origin: str

    def observations(self, start: date, end: date) -> list[Observation]: ...

    def rain(self, start: date, end: date) -> list[RainDay]: ...


@dataclass(frozen=True)
class PlotJob:
    plot_id: int
    planting_date: date
    season_type: str
    as_of: date


def _round(value: float | None, digits: int) -> float | None:
    return None if value is None else round(value, digits)


def _observation_row(obs: Observation, smoothed: dict[date, float]) -> dict:
    # A date with too few clear pixels has its pixel count but no NDVI (see apply_pixel_limit).
    return {
        "date": obs.day.isoformat(),
        "source": "S2",
        "ndvi": _round(obs.ndvi, 4),
        "smoothedNdvi": _round(smoothed.get(obs.day), 4),
        "clearPixels": obs.clear_pixels,
        "cloudPct": _round(obs.cloud_pct, 2),
    }


def compare_rain(job: PlotJob, source: DataSource) -> RainComparison | None:
    """Compares the last 30 days of available rain with the same dates in past years."""
    lookback = job.as_of - timedelta(days=params.RAIN_LOOKBACK_DAYS)
    available = [day for day in source.rain(lookback, job.as_of) if day.rain_mm is not None]
    if not available:
        return None

    end = max(day.day for day in available)
    start = end - timedelta(days=params.RAIN_WINDOW_DAYS - 1)
    past_totals = []
    # Stops before the window's first year, so a window crossing New Year can't be compared with itself.
    for year in range(params.BASELINE_FIRST_YEAR, start.year):
        past_start = same_day_in_year(start, year)
        past_end = past_start + timedelta(days=params.RAIN_WINDOW_DAYS - 1)
        total = complete_total(source.rain(past_start, past_end), past_start, past_end)
        if total is not None:
            past_totals.append(total)
    return rain_percent(complete_total(available, start, end), past_totals, end)


def _status_block(status: PlotStatus, as_of: date, rain: RainComparison | None, used: dict) -> dict:
    return {
        "statusDate": as_of.isoformat(),
        "status": status.status,
        "anomaly": _round(status.anomaly, 3),
        "rainPct": _round(status.rain_pct, 2),
        "cause": status.cause,
        "confidence": status.confidence,
        "explanation": {
            "needsFieldVisit": status.needs_field_visit,
            "signals": status.signals,
            "dataQualityNote": status.data_quality_note,
            "rainWindowEnd": rain.window_end.isoformat() if rain else None,
            "rainTotalMm": _round(rain.total_mm, 2) if rain else None,
            "rainNormalMm": _round(rain.normal_mm, 2) if rain else None,
        },
        "thresholdsUsed": used,
    }


def run(job: PlotJob, source: DataSource, thresholds: dict[str, float | None]) -> dict:
    if job.as_of < job.planting_date:
        raise ValueError("as_of is before the planting date")
    limits = StatusLimits.from_thresholds(thresholds)

    def valid(start: date, end: date) -> list[Observation]:
        return apply_pixel_limit(one_per_day(source.observations(start, end)), limits.min_clear_pixels)

    observations = valid(job.planting_date, job.as_of)
    smoothed = smooth(observations)
    rain = source.rain(job.planting_date, job.as_of)

    seasons = {
        start: valid(start, start + timedelta(days=params.BASELINE_MAX_DAP))
        for start in past_season_starts(job.planting_date)
    }
    bins = build_baseline(seasons)
    rain_comparison = compare_rain(job, source)
    status = plot_status(observations, smoothed, bins, job.planting_date, job.as_of, rain_comparison, limits)

    return {
        "plotId": job.plot_id,
        "dataOrigin": source.origin,
        "modelVersion": params.MODEL_VERSION,
        "plantingDate": job.planting_date.isoformat(),
        "inputFrom": job.planting_date.isoformat(),
        "inputTo": job.as_of.isoformat(),
        "params": {
            **params.as_dict(),
            "minClearPixels": limits.min_clear_pixels,
            "baselineSeasons": [start.isoformat() for start in seasons],
            "thresholds": thresholds,
        },
        "observations": [_observation_row(obs, smoothed) for obs in observations],
        "weather": [
            {"date": day.day.isoformat(), "rainMm": _round(day.rain_mm, 2)}
            for day in rain
            if day.rain_mm is not None
        ],
        "baseline": {
            "seasonType": job.season_type,
            "version": params.BASELINE_VERSION,
            "bins": [
                {"dapBin": b.dap_bin, "p10": round(b.p10, 4), "p50": round(b.p50, 4), "p90": round(b.p90, 4)}
                for b in bins
            ],
        },
        "status": _status_block(status, job.as_of, rain_comparison, limits.used(thresholds)),
    }
