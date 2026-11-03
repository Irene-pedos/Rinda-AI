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
| 7 | How many clear pixels does a plot need (suggested 20)? | "Not enough data" rule | §3.2 | Answered for the demo: 20, after the 10 m inward buffer (see question 15). |
| 13 | Are these processing settings acceptable? Cloud probability below 40, Sentinel-2 scene classes vegetation and bare soil only, a smoothing window of 3 images, a baseline of 150 days after planting, and at least 3 values per 10-day bin. The spec doesn't set them, so they are in `ml-service/app/params.py` as `TO_CONFIRM`. | Every NDVI reading and baseline | §3.2, §3.3 | Open |
| 14 | Did the demo plots grow maize in past seasons, and were they planted at about the same time each year? The MVP baseline assumes both. | Trust in the baseline | §10.1 | Open |
| 8 | Do agents only verify plots in the pilot, or admins too? | Plot verification | §12.7 | Open |
| 19 | Should a normal band estimated from its neighbouring bins be allowed to count towards a claim? In the back-test, 8 of 15 `AT_RISK` results used one. Each such status is already `LOW` confidence, and a person decides every claim. | Claim trigger | §3.7 | Open |
| 20 | How much data does a harvest forecast need? The spec suggests about 6 weeks after planting (`forecast_min_dap`, 42) but no amount of data. The `thresholds` table has `forecast_min_bins` as 3 periods, `TO_CONFIRM`. | Harvest forecast | §3.6 | Open |
| 21 | Should the harvest forecast count only the crop's own greenness? Today it divides plain NDVI totals, so bare soil (about 0.2) pulls every result towards 100%, and late stress barely shows (`ML_VALIDATION.md`). Options: subtract the plot's NDVI at planting first, or count the sensitive stage more. Either is a model change, so it needs the team's choice and a new back-test. | Harvest forecast, claim trigger | §3.6 | Open |
| 15 | How many valid observations are enough? | Plot status | §3.4 | Answered for the demo (`CONFIRMED_FOR_DEMO`, not final): a valid observation is a Sentinel-2 date with at least 20 clear pixels after the 10 m inward buffer. Status needs at least 1 in the last 20 days, otherwise `NOT_ENOUGH_DATA` with the last known status and date. The claim trigger needs at least 2. |
| 16 | What should "clearly below" normal mean for `AT_RISK`? | Plot status, claims | §3.4 | Answered for the demo (`CONFIRMED_FOR_DEMO`, not final): `WATCH` is an anomaly below −1 on the latest valid observation, or rain below 60% of normal over 30 days. `AT_RISK` needs an anomaly of −1.5 or lower. |
| 17 | Should "2 observations or 10 days" for `AT_RISK` be "or" or "and"? | Plot status, claims | §3.4 | Answered for the demo (`CONFIRMED_FOR_DEMO`, not final): "and". At least 2 consecutive valid observations meeting the `AT_RISK` condition, spanning at least 10 days from first to last. |
| 18 | How should the gaps in the normal curve be handled? In the back-test, 92 of 117 `NOT_ENOUGH_DATA` results came from growth stages with too few past readings, and only 25 from having no recent clear reading (`ML_VALIDATION.md`). The options are wider bins than 10 days, or filling a gap from the bins on each side. Lowering the 3 readings per bin would make the band less reliable. | Plot status, claims | §3.3 | Answered: fill a gap from the bins on each side. Gaps of up to 2 bins are filled, and filled bins are marked and give `LOW` confidence. |

## Other

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 9 | Should Kinyarwanda read-aloud use pre-recorded audio, or be dropped for now? | Android | §2.2, §12.6 | Open |
| 10 | Which confirmed past drought or flood seasons can be used for back-testing? | Validation | §3.8, §12.4 | Open |
| 11 | Will the demo run locally (laptop and phone on the same Wi-Fi) or on a free cloud host? | Deployment | §12.8 | Open |
| 12 | Who reviews and corrects the Kinyarwanda text? | Android | §12.9 | Open |
