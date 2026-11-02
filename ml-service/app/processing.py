"""The calculations, with no Earth Engine dependency, so they can be tested on fixed data."""

from dataclasses import dataclass
from datetime import date
from statistics import median

import numpy as np

from app import params


@dataclass(frozen=True)
class Observation:
    day: date
    ndvi: float | None
    clear_pixels: int
    cloud_pct: float | None = None

    @property
    def usable(self) -> bool:
        return self.ndvi is not None and self.clear_pixels >= params.MIN_CLEAR_PIXELS


@dataclass(frozen=True)
class RainDay:
    day: date
    rain_mm: float | None


@dataclass(frozen=True)
class BaselineBin:
    dap_bin: int
    p10: float
    p50: float
    p90: float


def ndvi(b8: float, b4: float) -> float | None:
    """Normalised difference vegetation index: (B8 - B4) / (B8 + B4)."""
    total = b8 + b4
    if total == 0:
        return None
    return (b8 - b4) / total


def one_per_day(observations: list[Observation]) -> list[Observation]:
    """A plot on a tile edge can appear twice on one date. Keeps the clearer image, sorted by date."""
    best: dict[date, Observation] = {}
    for obs in observations:
        current = best.get(obs.day)
        if current is None or obs.clear_pixels > current.clear_pixels:
            best[obs.day] = obs
    return [best[day] for day in sorted(best)]


def smooth(observations: list[Observation]) -> dict[date, float]:
    """Rolling median over usable observations, so one noisy image can't move the curve."""
    usable = [obs for obs in observations if obs.usable]
    values = [obs.ndvi for obs in usable]
    reach = params.SMOOTHING_WINDOW // 2
    return {
        obs.day: median(values[max(0, i - reach):i + reach + 1])
        for i, obs in enumerate(usable)
    }


def days_after_planting(day: date, planting: date) -> int:
    return (day - planting).days


def dap_bin(dap: int) -> int:
    return dap - dap % params.DAP_BIN_DAYS


def same_day_in_year(day: date, year: int) -> date:
    """The same calendar day in another year. 29 February becomes 28 February."""
    try:
        return day.replace(year=year)
    except ValueError:
        return day.replace(year=year, day=28)


def past_season_starts(planting: date) -> list[date]:
    """Planting dates to compare with: the same calendar day in each earlier year."""
    return [same_day_in_year(planting, year) for year in range(params.BASELINE_FIRST_YEAR, planting.year)]


def build_baseline(seasons: dict[date, list[Observation]]) -> list[BaselineBin]:
    """Builds p10, p50 and p90 by days after planting from past seasons.

    seasons maps each past planting date to that season's observations. Bins with too few
    usable values are left out, because a percentile of two numbers would look more certain
    than it is.
    """
    bins: dict[int, list[float]] = {}
    for planting, observations in seasons.items():
        for obs in observations:
            dap = days_after_planting(obs.day, planting)
            if obs.usable and 0 <= dap <= params.BASELINE_MAX_DAP:
                bins.setdefault(dap_bin(dap), []).append(obs.ndvi)

    result = []
    for key in sorted(bins):
        values = bins[key]
        if len(values) >= params.BASELINE_MIN_SAMPLES:
            p10, p50, p90 = np.percentile(values, [10, 50, 90])
            result.append(BaselineBin(key, float(p10), float(p50), float(p90)))
    return result
