# Open questions

Items the team must confirm. Until then, values that depend on them stay `TO_CONFIRM`; they must never be guessed. Numbering in the "Spec" column refers to `RINDA_PROJECT.md`.

## Blocking real results

| # | Question | Needed for | Spec | Status |
|---|---|---|---|---|
| 1 | Which district is the pilot, and what are the 3–5 real maize plot locations (boundaries or GPS points) and planting dates? | Demo plots, satellite extraction | §10.3, §12.3 | Open |
| 2 | What is the Google Cloud project ID registered for Earth Engine? | Real satellite data | §3.1, §10.3 | Answered: `rinda-ai`. Authorised and tested on the lab computer. |
| 3 | What trigger thresholds should the demo use (X days, Y% yield, Z% rain)? Please also confirm how the trigger reads them: Z is a rain **deficit** (100 − rain % of normal) of at least Z%, because §3.7's "rain deficit < Z%" would trigger on normal rain; and the yield part needs the **whole** forecast range below Y%, not just its low end. | Claim trigger | §3.7, §10.3 | Open. The trigger is built and tested, but creates no claim until X, Y and Z are in the `thresholds` table. |

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

## Plot registration and verification (handoff files `docs/00`-`04`, 2026-11-11)

| # | Question | Affects | Source | Status |
|---|---|---|---|---|
| 22 | Does the e-title or the official portal page show a plot sketch or coordinates? If yes, the walked shape can be compared; if no, only the area. | Automatic checks | Handoff 00, question 3 | Open: the team will check a real e-title. Until then only the area is compared. |
| 23 | What is the exact UPI format? NLA's FAQ suggests five slash-separated numbers (province/district/sector/cell/parcel) but explains it inconsistently. | UPI check | Handoff 00, question 2 | Open: checked loosely (five numeric parts, `rinda.plots.upi-pattern`). Make it strict only after checking a real e-title. The code-to-cell mapping is not hard-coded. |
| 24 | Is there a cell-level administrative boundary file with a licence that allows this use? Candidates to check: NSDI hub (geodata.rw), NISR geodata (statistics.gov.rw/geodata). | "Outside admin area" check | Handoff 00, question 2 | Open: the check is off (`rinda.plots.admin-area-check-enabled: false`) and every plot shows it as `NOT_RUN`. |
| 25 | What are the GPS accuracy limit for a walk point (`walk_max_accuracy_m`), the walked-vs-document area tolerance (`plot_area_mismatch_pct`) and the furthest a verification photo may be from the boundary (`verification_photo_max_distance_m`)? The overlap limit (`plot_max_overlap_pct`, 10%) is also still the spec's suggestion. | Walk, area and photo checks | Handoff 01 | Open: all `TO_CONFIRM` and empty. While empty, each fails safe: walks and areas go to a person, and the photo must be inside the boundary. Tune them with the real test farmer's walk. |
| 26 | How do tenants and relatives prove the right to farm a plot (owner OTP, signed note, lease)? | `NON_OWNER` flag | Handoff 00, question 6 | Open: a `LEASE` or `OWNER_CONSENT` upload reviewed by a person, behind `TenureConsentCheck`. |
| 27 | Does Law No. 058/2021 on personal data apply to NIDs, UPIs, GPS tracks and documents, and what does it require? Must be checked with a reliable source **before launch**. | Data handling | Handoff 00, question 7 | Open (pre-launch checklist). Nobody on the team has verified it. |

## Satellite monitoring (handoff file `docs/02`, 2026-11-13)

| # | Question | Affects | Source | Status |
|---|---|---|---|---|
| 28 | What NDVI values mark bare soil, clear green-up, the peak and the decline for maize in Rwanda, and how many days after planting should each stage show? Needs real, field-confirmed maize plots with known planting dates. | "Consistent with a maize season?" flag | Handoff 02 | Open: starting suggestions in `ml-service/app/params.py` (`SEASON_*`, TO_CONFIRM). On the placeholder demo plots most seasons give `NOT_ENOUGH_DATA` or `INCONSISTENT` (`ML_VALIDATION.md`), so expect review work until they are tuned. |
| 29 | Should an `INCONSISTENT` season result open a review flag at once, or only after it repeats on a later weekly fetch? On one demo plot-season it changed to `CONSISTENT` 50 days later. | Review workload, `SATELLITE_INCONSISTENT` flag | `ML_VALIDATION.md` | Open: it opens a flag at once (each stage already needs 2+ images); only a person closes it. |
| 30 | Which satellite and rainfall sources may Rinda use commercially? Earth Engine's free tier is for non-commercial use; CHIRPS, Planetary Computer and Copernicus Data Space each have their own terms. | Launch | Handoff 02 | Open (pre-launch checklist). The fetching layer is behind the `DataSource` interface in `app/pipeline.py`, so a source can be swapped. |
| 31 | How long should the weekly refresh keep going after planting (`rinda.monitoring.max-days-after-planting`, 200), and how many days of each fetch should the next one re-read for late images (`refresh-overlap-days`, 10)? | Weekly refresh | Handoff 02 | Open: starting values, TO_CONFIRM. |
| 32 | When NLA parcel data becomes available (official agreement only), what may a UPI lookup show to a farmer: the parcel shape and area only, or also the owner's name for a match check? Should lookups be limited per account, so nobody can browse parcels by trying UPIs? | `GET /land-registry/parcels`, a future NLA provider | Team request, 2026-11-13 | Open. Today only invented demo parcels exist (MOCK, dev only), with no owner name. |
