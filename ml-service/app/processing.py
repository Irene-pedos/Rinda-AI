"""The calculations, with no Earth Engine dependency, so they can be tested on fixed data."""

from collections.abc import Callable, Mapping
from dataclasses import dataclass, field, replace
from datetime import date, timedelta
from statistics import mean, median

import numpy as np

from app import params


@dataclass(frozen=True)
class Observation:
    day: date
    ndvi: float | None
    clear_pixels: int
    cloud_pct: float | None = None  # the whole Sentinel-2 scene, from the image's metadata
    cloud_fraction: float | None = None  # share of the plot's pixels masked as cloud, shadow or not clear

    @property
    def usable(self) -> bool:
        return self.ndvi is not None


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
    filled: bool = False


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


def apply_pixel_limit(observations: list[Observation], min_clear_pixels: int) -> list[Observation]:
    """Blanks the NDVI of dates with too few clear pixels, so they can't be mistaken for readings."""
    return [
        obs if obs.clear_pixels >= min_clear_pixels else replace(obs, ndvi=None)
        for obs in observations
    ]


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
    return fill_short_gaps(result)


def fill_short_gaps(bins: list[BaselineBin]) -> list[BaselineBin]:
    """Fills a short gap with a straight line between the bins on each side, marked as filled.

    The ends of the curve are never extended, because only one side is known there.
    """
    result = []
    for left, right in zip(bins, bins[1:]):
        result.append(left)
        missing = (right.dap_bin - left.dap_bin) // params.DAP_BIN_DAYS - 1
        if 0 < missing <= params.BASELINE_MAX_FILLED_BINS:
            for step in range(1, missing + 1):
                share = step / (missing + 1)
                result.append(BaselineBin(
                    left.dap_bin + step * params.DAP_BIN_DAYS,
                    left.p10 + (right.p10 - left.p10) * share,
                    left.p50 + (right.p50 - left.p50) * share,
                    left.p90 + (right.p90 - left.p90) * share,
                    filled=True,
                ))
    return result + bins[-1:]


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

    min_clear_pixels: int
    window_days: int
    min_observations: int
    watch_rain_pct: float
    watch_anomaly: float
    at_risk_anomaly: float
    consecutive_observations: int
    min_days: int

    KEYS = {
        "min_clear_pixels": "min_clear_pixels",
        "window_days": "not_enough_data_window_days",
        "min_observations": "not_enough_data_min_obs",
        "watch_rain_pct": "watch_rain_pct",
        "watch_anomaly": "watch_anomaly",
        "at_risk_anomaly": "at_risk_anomaly",
        "consecutive_observations": "at_risk_consecutive_obs",
        "min_days": "at_risk_min_days",
    }

    @classmethod
    def from_thresholds(cls, thresholds: Mapping[str, float | None]) -> "StatusLimits":
        missing = [key for key in cls.KEYS.values() if thresholds.get(key) is None]
        if missing:
            raise ValueError(f"These thresholds have no value yet: {', '.join(missing)}")
        return cls(
            min_clear_pixels=int(thresholds[cls.KEYS["min_clear_pixels"]]),
            window_days=int(thresholds[cls.KEYS["window_days"]]),
            min_observations=int(thresholds[cls.KEYS["min_observations"]]),
            watch_rain_pct=float(thresholds[cls.KEYS["watch_rain_pct"]]),
            watch_anomaly=float(thresholds[cls.KEYS["watch_anomaly"]]),
            at_risk_anomaly=float(thresholds[cls.KEYS["at_risk_anomaly"]]),
            consecutive_observations=int(thresholds[cls.KEYS["consecutive_observations"]]),
            min_days=int(thresholds[cls.KEYS["min_days"]]),
        )

    def used(self, thresholds: Mapping[str, float | None]) -> dict[str, float]:
        """The values a status depends on, to store with it."""
        return {key: thresholds[key] for key in self.KEYS.values()}


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


def _unbroken_run(usable: list[Observation], smoothed: dict[date, float], bands: dict[int, BaselineBin],
                  planting: date, meets: Callable[[float], bool]) -> list[Observation]:
    """The readings in a row, ending at the newest, whose anomaly meets the condition. Newest first."""
    run = []
    for obs in reversed(usable):
        band = bands.get(dap_bin(days_after_planting(obs.day, planting)))
        score = anomaly_score(smoothed[obs.day], band) if band else None
        if score is None or not meets(score):
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
    """Applies the team's status rules (questions 15 to 17) and the MVP's two cause rules (10.1)."""
    usable = [obs for obs in observations if obs.day in smoothed and obs.day <= as_of]
    first_day = as_of - timedelta(days=limits.window_days - 1)
    recent = [obs for obs in usable if obs.day >= first_day]
    rain_pct = rain.pct_of_normal if rain else None

    if not recent or len(recent) < limits.min_observations:
        return _not_enough(recent, limits, as_of, rain_pct, "NO_RECENT_READINGS")

    bands = {band.dap_bin: band for band in bins}
    latest = recent[-1]
    band = bands.get(dap_bin(days_after_planting(latest.day, planting)))
    score = anomaly_score(smoothed[latest.day], band) if band else None
    if score is None:
        return _not_enough(recent, limits, as_of, rain_pct, "NO_NORMAL_CURVE")

    below = _unbroken_run(usable, smoothed, bands, planting, lambda a: a < limits.watch_anomaly)
    severe = _unbroken_run(usable, smoothed, bands, planting, lambda a: a <= limits.at_risk_anomaly)
    severe_days = (severe[0].day - severe[-1].day).days if severe else 0
    rain_low = rain_pct is not None and rain_pct < limits.watch_rain_pct

    signals = []
    if below:
        signals.append({"code": "NDVI_BELOW_NORMAL", "readings": len(below)})
    if severe:
        signals.append({"code": "NDVI_WELL_BELOW_NORMAL", "readings": len(severe), "days": severe_days})
    if rain_low:
        signals.append({"code": "RAIN_BELOW_NORMAL", "rainPct": round(rain_pct, 1)})
    if rain_pct is None:
        signals.append({"code": "RAIN_NOT_COMPARED"})
    if band.filled:
        signals.append({"code": "NORMAL_CURVE_ESTIMATED"})

    if len(severe) >= limits.consecutive_observations and severe_days >= limits.min_days:
        status = "AT_RISK"
    elif below or rain_low:
        status = "WATCH"
    else:
        status = "HEALTHY"

    cause = None
    if below:
        cause = "WATER_DEFICIT" if rain_low else "UNEXPLAINED"

    # MEDIUM is the top level for now: nothing has been back-tested against field results.
    enough = len(recent) >= limits.consecutive_observations and rain_pct is not None
    confidence = "MEDIUM" if enough and cause != "UNEXPLAINED" and not band.filled else "LOW"

    return PlotStatus(status, confidence, score, rain_pct, cause, cause == "UNEXPLAINED", signals,
                      _quality_note(recent, limits, as_of))


@dataclass(frozen=True)
class ForecastLimits:
    """Decision values from the backend's thresholds table."""

    min_dap: int
    min_bins: int

    KEYS = {"min_dap": "forecast_min_dap", "min_bins": "forecast_min_bins"}

    @classmethod
    def from_thresholds(cls, thresholds: Mapping[str, float | None]) -> "ForecastLimits":
        missing = [key for key in cls.KEYS.values() if thresholds.get(key) is None]
        if missing:
            raise ValueError(f"These thresholds have no value yet: {', '.join(missing)}")
        return cls(int(thresholds[cls.KEYS["min_dap"]]), int(thresholds[cls.KEYS["min_bins"]]))

    def used(self, thresholds: Mapping[str, float | None]) -> dict[str, float]:
        return {key: thresholds[key] for key in self.KEYS.values()}


@dataclass(frozen=True)
class HarvestForecast:
    low_pct: float | None
    high_pct: float | None
    reason: str | None
    data_quality_note: str
    confidence: str = "LOW"  # It stays LOW until it has been compared with real harvests.


def harvest_range(observations: list[Observation], smoothed: dict[date, float], bins: list[BaselineBin],
                  planting: date, as_of: date, limits: ForecastLimits) -> HarvestForecast:
    """The MVP forecast (10.1): greenness so far against the plot's normal band, as a range.

    The range is this plot's total divided by the band's high and low edges, so its width comes
    from how much past seasons varied. It is a percent of normal greenness, not a tonnage.
    """
    note = ("This compares crop greenness with this plot's past seasons. It isn't a measured yield,"
            " and it hasn't been checked against real harvests.")
    dap = days_after_planting(as_of, planting)
    if dap < limits.min_dap:
        return HarvestForecast(None, None, "TOO_EARLY", note)

    bands = {band.dap_bin: band for band in bins}
    by_bin: dict[int, list[float]] = {}
    for obs in observations:
        key = dap_bin(days_after_planting(obs.day, planting))
        if obs.day in smoothed and obs.day <= as_of and key in bands:
            by_bin.setdefault(key, []).append(smoothed[obs.day])

    periods = dap_bin(dap) // params.DAP_BIN_DAYS + 1
    note = f"Based on {len(by_bin)} of {periods} 10-day periods since planting. {note}"
    low_total = sum(bands[key].p10 for key in by_bin)
    high_total = sum(bands[key].p90 for key in by_bin)
    if len(by_bin) < limits.min_bins or low_total <= 0 or high_total <= 0:
        return HarvestForecast(None, None, "NOT_ENOUGH_DATA", note)

    total = sum(mean(values) for values in by_bin.values())
    return HarvestForecast(total / high_total * 100, total / low_total * 100, None, note)


# ===== "Consistent with a maize season?" (docs/02-satellite-monitoring.md) =====

@dataclass(frozen=True)
class SeasonCheck:
    """A review flag, never proof of crop: satellites can't tell maize from sorghum or a maize-bean mix."""

    flag: str  # CONSISTENT, INCONSISTENT or NOT_ENOUGH_DATA
    reason: str
    confidence: str
    signals: list[str]
    data_quality_note: str
    images_used: int
    pixels_used: int | None  # clear pixels in the latest usable image
    latest_image_date: date | None


def season_check(observations: list[Observation], planting: date, as_of: date,
                 min_clear_pixels: int | None) -> SeasonCheck:
    """Compares the NDVI series since planting with the expected maize pattern: low (bare soil), then a rise,
    a peak and a decline toward harvest. Every stage needs more than one image, so one image never decides it.
    """
    if min_clear_pixels is None:
        return SeasonCheck("NOT_ENOUGH_DATA", "MIN_CLEAR_PIXELS_NOT_SET", "LOW", [],
                           "The minimum clear-pixel count is not set yet, so no image can be counted.", 0, None, None)

    def dap(obs: Observation) -> int:
        return days_after_planting(obs.day, planting)

    usable = [obs for obs in one_per_day(observations)
              if planting <= obs.day <= as_of and obs.ndvi is not None and obs.clear_pixels >= min_clear_pixels]
    latest = usable[-1] if usable else None
    note = _season_note(usable, min_clear_pixels)
    days_now = days_after_planting(as_of, planting)
    need = params.SEASON_MIN_IMAGES_PER_STAGE

    def result(flag: str, reason: str, signals: list[str]) -> SeasonCheck:
        # Never HIGH: these rules have not been checked against Rwandan fields. Small plots with few
        # clear pixels or few images stay LOW.
        strong = len(usable) >= 2 * need and latest is not None and min(o.clear_pixels for o in usable) >= 2 * min_clear_pixels
        confidence = "MEDIUM" if flag != "NOT_ENOUGH_DATA" and strong else "LOW"
        return SeasonCheck(flag, reason, confidence, signals, note, len(usable),
                           latest.clear_pixels if latest else None, latest.day if latest else None)

    if len(usable) < need:
        return result("NOT_ENOUGH_DATA", "FEW_CLEAR_IMAGES", [])

    early = [obs for obs in usable if dap(obs) <= params.SEASON_EARLY_END_DAP]
    later = [obs for obs in usable if dap(obs) > params.SEASON_EARLY_END_DAP]
    green_later = [obs for obs in later if obs.ndvi >= params.SEASON_GREEN_NDVI_MIN]
    bare_start = any(obs.ndvi <= params.SEASON_BARE_NDVI_MAX for obs in early)
    signals = []
    if bare_start:
        signals.append("BARE_START_SEEN")

    # Green when the field should be bare: another cover (grass, a perennial crop) or a wrong planting date.
    if len(early) >= need and all(obs.ndvi >= params.SEASON_GREEN_NDVI_MIN for obs in early):
        return result("INCONSISTENT", "GREEN_AT_PLANTING", signals + ["GREEN_AT_PLANTING"])

    peak = max(later, key=lambda obs: obs.ndvi) if later else None
    rose = (peak is not None and len(green_later) >= need and early
            and peak.ndvi - min(obs.ndvi for obs in early) >= params.SEASON_MIN_RISE)
    if rose:
        signals.append("GROWTH_SEEN")
    declined = peak is not None and any(
        obs.day > peak.day and obs.ndvi <= peak.ndvi - params.SEASON_MIN_DECLINE for obs in later)
    if declined:
        signals.append("DECLINE_SEEN")

    # Still green long after a maize crop should be drying: a longer-lived cover, or a later planting.
    late = [obs for obs in usable if dap(obs) >= params.SEASON_END_DAP]
    if (len(late) >= need and peak is not None
            and all(obs.ndvi >= params.SEASON_GREEN_NDVI_MIN and obs.ndvi > peak.ndvi - params.SEASON_MIN_DECLINE
                    for obs in late)):
        return result("INCONSISTENT", "STILL_GREEN_AFTER_SEASON", signals + ["NO_DECLINE"])

    # Bare all season: no green-up by the time maize should clearly show.
    after_growth = [obs for obs in usable if dap(obs) >= params.SEASON_GROWTH_EXPECTED_DAP]
    if days_now >= params.SEASON_GROWTH_EXPECTED_DAP and len(after_growth) >= need and not green_later:
        return result("INCONSISTENT", "NO_GROWTH_SEEN", signals + ["NO_GROWTH"])

    if bare_start and rose:
        return result("CONSISTENT", "SEASON_PATTERN_SEEN" if declined else "CONSISTENT_SO_FAR", signals)

    if not early:
        return result("NOT_ENOUGH_DATA", "NO_CLEAR_IMAGE_AFTER_PLANTING", signals)
    if days_now < params.SEASON_GROWTH_EXPECTED_DAP:
        return result("NOT_ENOUGH_DATA", "TOO_EARLY", signals)
    return result("NOT_ENOUGH_DATA", "PATTERN_UNCLEAR", signals)


def _season_note(usable: list[Observation], min_clear_pixels: int) -> str:
    if not usable:
        return f"No image since planting had {min_clear_pixels} or more clear pixels."
    pixels = sorted(obs.clear_pixels for obs in usable)
    return (f"{len(usable)} clear image(s) since planting, latest {usable[-1].day.isoformat()}, "
            f"median {int(median(pixels))} clear pixels each (minimum {min_clear_pixels}). "
            "A review flag, not proof of crop: these rules have not been checked against field results yet.")
