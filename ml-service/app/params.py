"""Processing settings. Every run stores these with its results, so results can be reproduced.

Items marked TO_CONFIRM are suggestions. Decision values, such as the clear pixel limit, are not
here: each run reads them from the backend's thresholds table and stores what it used.
Change MODEL_VERSION whenever a value here changes.
"""

# 0.6.0: the median of each image's clear pixels instead of the mean; adds the maize season check.
MODEL_VERSION = "0.6.0"
BASELINE_VERSION = "plot-history-v2"

# Spec section 3.2
SHRINK_METRES = 10
SENTINEL2_SCALE_METRES = 10
# docs/02-satellite-monitoring.md: the median of the clear pixels, so a few edge or haze pixels can't pull it.
NDVI_REDUCER = "median"

# Not in the spec, so TO_CONFIRM (docs/OPEN_QUESTIONS.md).
CLOUD_PROBABILITY_MAX = 40
SCL_CLEAR_CLASSES = [4, 5]  # Sentinel-2 scene classes: vegetation, bare soil
SMOOTHING_WINDOW = 3
BASELINE_MAX_DAP = 150
BASELINE_MIN_SAMPLES = 3
BASELINE_MAX_FILLED_BINS = 2  # A longer gap could cut across the greenness peak (question 18).

# Spec sections 3.3 and 10.1
BASELINE_FIRST_YEAR = 2019
DAP_BIN_DAYS = 10

CHIRPS_SCALE_METRES = 5566

# Spec section 3.4 says 30 days. CHIRPS lags, so the window ends on its latest day within the lookback.
RAIN_WINDOW_DAYS = 30
RAIN_LOOKBACK_DAYS = 60  # TO_CONFIRM
RAIN_MIN_PAST_SEASONS = 3  # TO_CONFIRM


# "Consistent with a maize season?" (docs/02-satellite-monitoring.md). A review flag, never proof of crop.
# All TO_CONFIRM: general-knowledge starting values for maize NDVI, not tuned on Rwandan fields.
SEASON_EARLY_END_DAP = 30         # the "bare soil" window: planting day to this many days after planting
SEASON_BARE_NDVI_MAX = 0.35       # at or below: bare soil or a just-emerged crop
SEASON_GREEN_NDVI_MIN = 0.5       # at or above: clearly green (a crop near its peak, or dense cover)
SEASON_MIN_RISE = 0.15            # the peak must be at least this much above the early low
SEASON_MIN_DECLINE = 0.1          # a drop from the peak of at least this much counts as the decline
SEASON_GROWTH_EXPECTED_DAP = 70   # by this day, a maize crop should have shown its rise
SEASON_END_DAP = 150              # after this day, a maize crop should be drying toward harvest
SEASON_MIN_IMAGES_PER_STAGE = 2   # one image never decides a stage (persistence rule)


def season_params() -> dict:
    return {
        "earlyEndDap": SEASON_EARLY_END_DAP,
        "bareNdviMax": SEASON_BARE_NDVI_MAX,
        "greenNdviMin": SEASON_GREEN_NDVI_MIN,
        "minRise": SEASON_MIN_RISE,
        "minDecline": SEASON_MIN_DECLINE,
        "growthExpectedDap": SEASON_GROWTH_EXPECTED_DAP,
        "seasonEndDap": SEASON_END_DAP,
        "minImagesPerStage": SEASON_MIN_IMAGES_PER_STAGE,
        "ndviReducer": NDVI_REDUCER,
        "shrinkMetres": SHRINK_METRES,
        "cloudProbabilityMax": CLOUD_PROBABILITY_MAX,
        "sclClearClasses": SCL_CLEAR_CLASSES,
    }


def as_dict() -> dict:
    return {
        "shrinkMetres": SHRINK_METRES,
        "ndviReducer": NDVI_REDUCER,
        "cloudProbabilityMax": CLOUD_PROBABILITY_MAX,
        "sclClearClasses": SCL_CLEAR_CLASSES,
        "smoothingWindow": SMOOTHING_WINDOW,
        "baselineFirstYear": BASELINE_FIRST_YEAR,
        "baselineMaxDap": BASELINE_MAX_DAP,
        "baselineMinSamples": BASELINE_MIN_SAMPLES,
        "baselineMaxFilledBins": BASELINE_MAX_FILLED_BINS,
        "dapBinDays": DAP_BIN_DAYS,
        "rainWindowDays": RAIN_WINDOW_DAYS,
        "rainLookbackDays": RAIN_LOOKBACK_DAYS,
        "rainMinPastSeasons": RAIN_MIN_PAST_SEASONS,
    }
