# Open questions

Items the team must confirm. Until then, values that depend on them stay `TO_CONFIRM`; they must never be guessed. Numbering in the "Spec" column refers to `RINDA_PROJECT.md`.

## Blocking real results

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 1 | Which district is the pilot, and what are the 3–5 real maize plot locations (boundaries or GPS points) and planting dates? | Demo plots, satellite extraction | §10.3, §12.3 | Open |
| 2 | What is the Google Cloud project ID registered for Earth Engine? | Real satellite data | §3.1, §10.3 | Answered: `rinda-ai`. Authorised and tested on the lab computer. |
| 3 | What trigger thresholds should the demo use (X days, Y% yield, Z% rain)? | Claim trigger | §3.7, §10.3 | Open |

## Insurer rules

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 4 | What are the covered perils, sum insured per hectare, premium or subsidy, and payout rates? | Coverage screen, payout amount | §3.7, §12.1 | Open |

## Season and plot rules

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 5 | What are the exact Season A and Season B date windows for maize? | Planting date validation | §5.2, §12.2 | Open |
| 6 | What are the minimum and maximum plot area, and the maximum allowed overlap? The backend currently applies the spec's suggested 0.1 ha minimum; no maximum is enforced. | Plot validation | §3.2, §5.2, §12.5 | Open |
| 7 | How many clear pixels does a plot need (suggested 20)? | "Not enough data" rule | §3.2 | Open |
| 13 | Are these processing settings acceptable? Cloud probability below 40, Sentinel-2 scene classes vegetation and bare soil only, a smoothing window of 3 images, a baseline of 150 days after planting, and at least 3 values per 10-day bin. The spec doesn't set them, so they are in `ml-service/app/params.py` as `TO_CONFIRM`. | Every NDVI reading and baseline | §3.2, §3.3 | Open |
| 14 | Did the demo plots grow maize in past seasons, and were they planted at about the same time each year? The MVP baseline assumes both. | Trust in the baseline | §10.1 | Open |
| 8 | Do agents only verify plots in the pilot, or admins too? | Plot verification | §12.7 | Open |
| 15 | How many valid observations in the last 15 days are enough? The spec says "too few" with no number. The `thresholds` table has 1 as `TO_CONFIRM`, the mildest reading. In testing, only about 4 in 10 Sentinel-2 dates were usable in the rainy season, so a higher number would give many more `NOT_ENOUGH_DATA` results. | Plot status | §3.4 | Open |
| 16 | What should "clearly below" normal mean for `AT_RISK`? The spec suggests an anomaly of −1 or lower, but the band edge p10 is exactly −1 by its own formula. So every reading below the edge already meets it, and any two low readings in a row become `AT_RISK`. A lower value, such as −1.5, would make the rule stricter, but that value is the team's to set. | Plot status, claims | §3.4 | Open |
| 17 | Should "2 observations or 10 days" for `AT_RISK` stay as "or"? As written, 10 days can only happen with 2 or more readings, so `at_risk_min_days` currently has no effect. | Plot status, claims | §3.4 | Open |

## Other

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 9 | Should Kinyarwanda read-aloud use pre-recorded audio, or be dropped for now? | Android | §2.2, §12.6 | Open |
| 10 | Which confirmed past drought or flood seasons can be used for back-testing? | Validation | §3.8, §12.4 | Open |
| 11 | Will the demo run locally (laptop and phone on the same Wi-Fi) or on a free cloud host? | Deployment | §12.8 | Open |
| 12 | Who reviews and corrects the Kinyarwanda text? | Android | §12.9 | Open |
