"""Processing settings. Every run stores these with its results, so results can be reproduced.

Items marked TO_CONFIRM are suggestions. Decision values, such as the clear pixel limit, are not
here: each run reads them from the backend's thresholds table and stores what it used.
Change MODEL_VERSION whenever a value here changes.
"""

MODEL_VERSION = "0.5.0"
BASELINE_VERSION = "plot-history-v2"

# Spec section 3.2
SHRINK_METRES = 10
SENTINEL2_SCALE_METRES = 10

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


def as_dict() -> dict:
    return {
        "shrinkMetres": SHRINK_METRES,
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
