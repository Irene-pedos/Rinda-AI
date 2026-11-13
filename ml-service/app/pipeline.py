"""Turns a data source into the result the backend stores."""

from dataclasses import dataclass
from datetime import date, timedelta
from typing import Protocol

from app import params
from app.processing import (
    Observation,
    ForecastLimits,
    HarvestForecast,
    PlotStatus,
    RainComparison,
    RainDay,
    StatusLimits,
    apply_pixel_limit,
    build_baseline,
    complete_total,
    harvest_range,
    one_per_day,
    past_season_starts,
    plot_status,
    rain_percent,
    same_day_in_year,
    season_check,
    smooth,
)


class DataSource(Protocol):
    """Where images and rainfall come from. Earth Engine and the simulated sample implement it; another provider
    (Planetary Computer, Copernicus Data Space) can be added without changing the API or the calculations."""

    origin: str

    def observations(self, start: date, end: date) -> list[Observation]: ...

    def rain(self, start: date, end: date) -> list[RainDay]: ...


@dataclass(frozen=True)
class MonitorJob:
    """One satellite fetch. history is what the backend already stored for this boundary, so a weekly refresh
    fetches only new images while the season check still sees the whole season."""

    plot_id: int
    planting_date: date
    fetch_from: date
    as_of: date
    min_clear_pixels: int | None
    history: tuple[Observation, ...] = ()


def monitor(job: MonitorJob, source: DataSource) -> dict:
    """Fetches the raw observations and rainfall in the window and checks the season so far. Saves nothing:
    the backend stores what this returns."""
    if job.as_of < job.fetch_from:
        raise ValueError("asOf is before fetchFrom")
    fetched = one_per_day(source.observations(job.fetch_from, job.as_of))
    rain = [day for day in source.rain(job.fetch_from, job.as_of) if day.rain_mm is not None]
    merged = {obs.day: obs for obs in job.history}
    merged.update({obs.day: obs for obs in fetched})
    check = season_check(list(merged.values()), job.planting_date, job.as_of, job.min_clear_pixels)
    return {
        "plotId": job.plot_id,
        "dataOrigin": source.origin,
        "modelVersion": params.MODEL_VERSION,
        "plantingDate": job.planting_date.isoformat(),
        "fetchFrom": job.fetch_from.isoformat(),
        "fetchTo": job.as_of.isoformat(),
        "params": {**params.season_params(), "minClearPixels": job.min_clear_pixels},
        # Raw values, with no pixel limit applied, so the rules can change later without fetching again.
        "observations": [
            {
                "date": obs.day.isoformat(),
                "source": "S2",
                "ndviMedian": _round(obs.ndvi, 4),
                "pixelCount": obs.clear_pixels,
                "cloudFraction": _round(obs.cloud_fraction, 4),
                "sceneCloudPct": _round(obs.cloud_pct, 2),
            }
            for obs in fetched
        ],
        "rainfall": [{"date": day.day.isoformat(), "rainMm": _round(day.rain_mm, 2), "source": "CHIRPS"}
                     for day in rain],
        "seasonCheck": {
            "checkDate": job.as_of.isoformat(),
            "flag": check.flag,
            "reason": check.reason,
            "confidence": check.confidence,
            "signals": check.signals,
            "dataQualityNote": check.data_quality_note,
            "imagesUsed": check.images_used,
            "pixelsUsed": check.pixels_used,
            "latestImageDate": check.latest_image_date.isoformat() if check.latest_image_date else None,
        },
    }


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


def _forecast_block(forecast: HarvestForecast, as_of: date, used: dict) -> dict:
    return {
        "forecastDate": as_of.isoformat(),
        "lowPct": _round(forecast.low_pct, 2),
        "highPct": _round(forecast.high_pct, 2),
        "confidence": forecast.confidence,
        "reason": forecast.reason,
        "dataQualityNote": forecast.data_quality_note,
        "thresholdsUsed": used,
    }


def run(job: PlotJob, source: DataSource, thresholds: dict[str, float | None]) -> dict:
    if job.as_of < job.planting_date:
        raise ValueError("as_of is before the planting date")
    limits = StatusLimits.from_thresholds(thresholds)
    forecast_limits = ForecastLimits.from_thresholds(thresholds)

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
    forecast = harvest_range(observations, smoothed, bins, job.planting_date, job.as_of, forecast_limits)

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
                {"dapBin": b.dap_bin, "p10": round(b.p10, 4), "p50": round(b.p50, 4), "p90": round(b.p90, 4),
                 "filled": b.filled}
                for b in bins
            ],
        },
        "status": _status_block(status, job.as_of, rain_comparison, limits.used(thresholds)),
        "forecast": _forecast_block(forecast, job.as_of, forecast_limits.used(thresholds)),
    }
