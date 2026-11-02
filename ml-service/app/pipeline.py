"""Turns a data source into the result the backend stores."""

from dataclasses import dataclass
from datetime import date, timedelta
from typing import Protocol

from app import params
from app.processing import Observation, RainDay, build_baseline, one_per_day, past_season_starts, smooth


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
    # A date with too few clear pixels keeps its pixel count but no NDVI, so it can't be mistaken for a reading.
    return {
        "date": obs.day.isoformat(),
        "source": "S2",
        "ndvi": _round(obs.ndvi, 4) if obs.usable else None,
        "smoothedNdvi": _round(smoothed.get(obs.day), 4),
        "clearPixels": obs.clear_pixels,
        "cloudPct": _round(obs.cloud_pct, 2),
    }


def run(job: PlotJob, source: DataSource) -> dict:
    if job.as_of < job.planting_date:
        raise ValueError("as_of is before the planting date")

    observations = one_per_day(source.observations(job.planting_date, job.as_of))
    smoothed = smooth(observations)
    rain = source.rain(job.planting_date, job.as_of)

    seasons = {
        start: one_per_day(source.observations(start, start + timedelta(days=params.BASELINE_MAX_DAP)))
        for start in past_season_starts(job.planting_date)
    }
    bins = build_baseline(seasons)

    return {
        "plotId": job.plot_id,
        "dataOrigin": source.origin,
        "modelVersion": params.MODEL_VERSION,
        "plantingDate": job.planting_date.isoformat(),
        "inputFrom": job.planting_date.isoformat(),
        "inputTo": job.as_of.isoformat(),
        "params": {**params.as_dict(), "baselineSeasons": [start.isoformat() for start in seasons]},
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
    }
