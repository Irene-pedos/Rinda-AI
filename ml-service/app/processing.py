"""The calculations, with no Earth Engine dependency, so they can be tested on fixed data."""

from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import date, timedelta
from statistics import mean, median

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


@dataclass(frozen=True)
class RainComparison:
    window_end: date
    total_mm: float
    normal_mm: float
    pct_of_normal: float


def complete_total(days: list[RainDay], start: date, end: date) -> float | None:
    """Rain over a window, or None if any day is missing. A missing day isn't a dry day."""
    values = {day.day: day.rain_mm for day in days if day.rain_mm is not None and start <= day.day <= end}
    if len(values) != (end - start).days + 1:
        return None
    return sum(values.values())


def rain_percent(total_mm: float | None, past_totals: list[float], window_end: date) -> RainComparison | None:
    """Rain as a percent of the past seasons' average. None means it can't be compared."""
    if total_mm is None or len(past_totals) < params.RAIN_MIN_PAST_SEASONS:
        return None
    normal = mean(past_totals)
    if normal <= 0:
        return None
    return RainComparison(window_end, total_mm, normal, total_mm / normal * 100)


@dataclass(frozen=True)
class StatusLimits:
    """Decision values from the backend's thresholds table."""

    window_days: int
    min_observations: int
    watch_rain_pct: float
    consecutive_observations: int
    min_days: int
    anomaly: float

    KEYS = {
        "window_days": "not_enough_data_window_days",
        "min_observations": "not_enough_data_min_obs",
        "watch_rain_pct": "watch_rain_pct",
        "consecutive_observations": "at_risk_consecutive_obs",
        "min_days": "at_risk_min_days",
        "anomaly": "at_risk_anomaly",
    }

    @classmethod
    def from_thresholds(cls, thresholds: Mapping[str, float | None]) -> "StatusLimits":
        missing = [key for key in cls.KEYS.values() if thresholds.get(key) is None]
        if missing:
            raise ValueError(f"These thresholds have no value yet: {', '.join(missing)}")
        return cls(
            window_days=int(thresholds[cls.KEYS["window_days"]]),
            min_observations=int(thresholds[cls.KEYS["min_observations"]]),
            watch_rain_pct=float(thresholds[cls.KEYS["watch_rain_pct"]]),
            consecutive_observations=int(thresholds[cls.KEYS["consecutive_observations"]]),
            min_days=int(thresholds[cls.KEYS["min_days"]]),
            anomaly=float(thresholds[cls.KEYS["anomaly"]]),
        )


@dataclass(frozen=True)
class PlotStatus:
    status: str
    confidence: str
    anomaly: float | None = None
    rain_pct: float | None = None
    cause: str | None = None
    needs_field_visit: bool = False
    signals: list[dict] = field(default_factory=list)
    data_quality_note: str = ""


def anomaly_score(value: float, band: BaselineBin) -> float | None:
    """(NDVI - p50) / (p50 - p10). None if the band has no width to measure against."""
    width = band.p50 - band.p10
    if width <= 0:
        return None
    return (value - band.p50) / width


def _below_band_run(usable: list[Observation], smoothed: dict[date, float],
                    bands: dict[int, BaselineBin], planting: date) -> list[Observation]:
    """The unbroken run of readings below the band, newest first."""
    run = []
    for obs in reversed(usable):
        band = bands.get(dap_bin(days_after_planting(obs.day, planting)))
        if band is None or smoothed[obs.day] >= band.p10:
            break
        run.append(obs)
    return run


def _quality_note(recent: list[Observation], limits: StatusLimits, as_of: date) -> str:
    latest = f" Latest: {recent[-1].day.isoformat()}." if recent else ""
    return (f"{len(recent)} clear reading(s) in the {limits.window_days} days to {as_of.isoformat()}.{latest}"
            " These rules have not been checked against field results yet.")


def _not_enough(recent: list[Observation], limits: StatusLimits, as_of: date, rain_pct: float | None,
                code: str) -> PlotStatus:
    return PlotStatus("NOT_ENOUGH_DATA", "LOW", rain_pct=rain_pct, signals=[{"code": code}],
                      data_quality_note=_quality_note(recent, limits, as_of))


def plot_status(observations: list[Observation], smoothed: dict[date, float], bins: list[BaselineBin],
                planting: date, as_of: date, rain: RainComparison | None, limits: StatusLimits) -> PlotStatus:
    """Applies the spec's status rules (3.4) and the MVP's two cause rules (10.1)."""
    usable = [obs for obs in observations if obs.day in smoothed and obs.day <= as_of]
    first_day = as_of - timedelta(days=limits.window_days - 1)
    recent = [obs for obs in usable if obs.day >= first_day]
    rain_pct = rain.pct_of_normal if rain else None

    if not recent or len(recent) < limits.min_observations:
        return _not_enough(recent, limits, as_of, rain_pct, "NO_RECENT_READINGS")

    bands = {band.dap_bin: band for band in bins}
    latest = recent[-1]
    band = bands.get(dap_bin(days_after_planting(latest.day, planting)))
    if band is None:
        return _not_enough(recent, limits, as_of, rain_pct, "NO_NORMAL_CURVE")

    score = anomaly_score(smoothed[latest.day], band)
    below = _below_band_run(usable, smoothed, bands, planting)
    rain_low = rain_pct is not None and rain_pct < limits.watch_rain_pct

    signals = []
    if below:
        signals.append({"code": "NDVI_BELOW_NORMAL", "readings": len(below)})
    if rain_low:
        signals.append({"code": "RAIN_BELOW_NORMAL", "rainPct": round(rain_pct, 1)})
    if rain_pct is None:
        signals.append({"code": "RAIN_NOT_COMPARED"})

    persisted = below and (len(below) >= limits.consecutive_observations
                           or (below[0].day - below[-1].day).days >= limits.min_days)
    clearly_low = score is not None and score <= limits.anomaly
    if persisted and clearly_low:
        status = "AT_RISK"
    elif below or rain_low:
        status = "WATCH"
    else:
        status = "HEALTHY"

    cause = None
    if below:
        cause = "WATER_DEFICIT" if rain_low else "UNEXPLAINED"

    # MEDIUM is the top level for now: nothing has been back-tested, so HIGH would claim more than we know.
    enough = len(recent) >= limits.consecutive_observations and rain_pct is not None
    confidence = "MEDIUM" if enough and cause != "UNEXPLAINED" else "LOW"

    return PlotStatus(status, confidence, score, rain_pct, cause, cause == "UNEXPLAINED", signals,
                      _quality_note(recent, limits, as_of))
