# ML validation

What has been tested, what the results show, and what they can't show. For how the rules work, see `RINDA_PROJECT.md` §3 and questions 15 to 18 in `OPEN_QUESTIONS.md`.

**There is no accuracy figure in this file, because there is no field ground truth yet.** This back-test counts how often each result appears. It can't say whether any result was correct.

## Back-test of the demo settings

Run on 2026-11-13 with model version 0.6.0, which takes the **median** NDVI of each image's clear pixels (0.5.0 took the mean), using the `CONFIRMED_FOR_DEMO` values in the `thresholds` table. The 0.5.0 run was on 2026-11-03; the difference is in the next section. To repeat it, run this from `ml-service` with the backend running:

```powershell
.venv\Scripts\python -m app.backtest
```

It saves nothing to the database.

### How it was run

- **Replay:** the rules ran every 5 days through each season, up to 150 days after planting (DAP). Each result used only the data available on that date, and each baseline used only earlier years.
- **Real data:** Sentinel-2 and CHIRPS for the 4 demo plots, seasons 2023 to 2025, 360 results in total.
- **Simulated data:** two normal seasons and one with stress built in from 55 DAP. The stressed season stops at 81 DAP.
- **Normal band:** gaps of up to 2 bins are estimated from the bins on each side (question 18).

### Limits

- **These aren't known maize fields.** The demo plots are placeholder locations, so the counts show how the rules behave, not how maize behaved.
- **The planting date is assumed.** Every season uses 20 September, because the demo plots have no confirmed date (question 1).
- **The plots aren't independent.** All four are within about 1.2 km of each other, and likely share one CHIRPS rain pixel.
- **No season is a known drought or a known good year** (question 10), so false alarms and missed events can't be measured.


## Median instead of mean (model 0.6.0)

`docs/02-satellite-monitoring.md` asks for the median over a plot's usable pixels, so a few edge, haze or shadow pixels that the cloud mask misses can't pull the value. Nothing else changed between the two runs: same plots, seasons, thresholds and replay dates.

| Real data, 360 results | 0.5.0 (mean) | 0.6.0 (median) |
|---|---|---|
| `HEALTHY` | 217 (60%) | 204 (57%) |
| `WATCH` | 62 (17%) | 74 (21%) |
| `AT_RISK` | 15 (4%) | 16 (4%) |
| `NOT_ENOUGH_DATA` | 66 (18%) | 66 (18%) |
| `AT_RISK` in the sensitive stage (50 to 100 DAP) | 3 | 5 |
| `LOW` confidence | 226 (63%) | 232 (64%) |

1. **The median gave 13 fewer `HEALTHY` results, 11 of them on plot 9004** (2024 alone: 7 fewer `HEALTHY`, 3 more `WATCH`, 4 more `AT_RISK`). The other plots changed by 0 to 4 results per season. `NOT_ENOUGH_DATA` didn't change, because it depends on how many images pass the pixel limit, not on their value.
2. **Plot 9004's `AT_RISK` moved.** In 2024 it gained a run from 65 to 80 DAP; in 2025 it lost the run at 55 and 60 DAP. One image's value can move a whole run, which shows how sensitive the rules are on these small plots.
3. **The simulated sample didn't change,** because it stores one value per image and isn't reduced again.
4. **The harvest ranges moved by a few points** and stayed as wide (14 to 56 points at 80 DAP).
5. **This doesn't say which is more accurate.** There is no field ground truth, so neither run can be called better; the median is used because the spec asks for it and it resists outlier pixels.

## Effect of filling the gaps (measured with model 0.5.0)

| Real data, 360 results | Before | After |
|---|---|---|
| `HEALTHY` | 187 (52%) | 217 (60%) |
| `WATCH` | 54 (15%) | 62 (17%) |
| `AT_RISK` | 2 (1%) | 15 (4%) |
| `NOT_ENOUGH_DATA` | 117 (32%) | 66 (18%) |
| `NOT_ENOUGH_DATA` from gaps in the normal band | 92 | 41 |

The simulated results didn't change, because the sample has no gaps of this kind.

## What the results show

These figures are from model 0.6.0 (median).

1. **Filling the gaps removed over half of the `NOT_ENOUGH_DATA` results, but 17% of statuses rest on an estimated band.** That's 51 of 294, and all of them are `LOW` confidence.
2. **Most remaining `NOT_ENOUGH_DATA` is in the 2023 season:** 57 of 66. That season has only 4 past seasons, so its gaps are longer than 2 bins. Later seasons have more history, and were at 7% or lower.
3. **`AT_RISK` is still uncommon, but most of it doesn't look like crop stress.** Of the 16 results, 5 fall in the sensitive stage of 50 to 100 DAP (4 of them plot 9004 in 2024). Another 9 come at 120 DAP or later, when maize may be drying off or already harvested, so a small timing difference looks like a large drop. The last 2 are at 15 and 20 DAP, before the crop is established. A claim trigger limited to the sensitive stage would see only those 5.
4. **8 of the 16 `AT_RISK` results use an estimated band.** One of those is in the sensitive stage. Question 19 asks whether an estimated band should be allowed to count towards a claim.
5. **The rules do pick up the simulated stress, but late.** Stress began at 55 DAP and `AT_RISK` first appeared at 75 DAP, 20 days later. The 2 normal simulated seasons never reached `AT_RISK` in 60 results. They did reach `WATCH` in 9 of those results, or 15%.
6. **`at_risk_anomaly` and `at_risk_min_days` both change `AT_RISK`.** An anomaly limit of −2 lowers it from 16 to 10, and −1.25 raises it to 21. A minimum of 15 days gives 13, and 5 days gives 21.
7. **Anomaly scores can be very large,** down to −11.18. Narrow normal bands may be the reason, so a score far below −1.5 doesn't always mean a severe drop.
8. **Raising `min_clear_pixels` to 100 raised `AT_RISK` from 16 to 23.** It also raised `NOT_ENOUGH_DATA` to 26%. The cause wasn't investigated. It may be that fewer readings change the baseline, so treat this with care.
9. **Status moves often.** Each 30-result season changed status 7 to 11 times on real data, and this count includes moves in and out of `NOT_ENOUGH_DATA`. So health alerts only go out when a plot gets worse than its last known status.
10. **Most results are low confidence.** 64% of real results were `LOW`, and none were `HIGH`, because `HIGH` isn't used before back-testing against field results.
11. **Most low NDVI has no explained cause.** Of the 64 real results with low NDVI, 58 were `UNEXPLAINED` and 6 were `WATER_DEFICIT`, so `needsFieldVisit` would come up often. Another 26 `WATCH` results came from low rain alone, with NDVI inside the band.

## Harvest forecast

The forecast is the plot's greenness total so far, divided by the normal band's high and low totals (§10.1). It's a percent of normal greenness, not a yield.

1. **It reacts weakly to stress late in the season.** At 80 DAP, the stressed simulated season gave 88% to 100%, and the two normal seasons gave 97% to 111% and 96% to 109%, so the ranges overlap. The stressed plot was well below normal for its last 3 periods, but its early periods were slightly above normal, and every period counts the same.
2. **Bare soil keeps every total high.** NDVI is around 0.2 even before the crop grows, and that part is counted in both the plot and its normal, which pulls every result towards 100%. A plot that failed completely after 55 DAP would still score well above 0%. Question 21 asks whether to change this.
3. **Real ranges are wide, as the spec expects (§3.6).** At 80 DAP they were 14 to 56 points wide. They're wide because past seasons varied a lot, and they weren't narrowed.
4. **Plot 9004 had the lowest ranges,** 71% to 86% in 2024. It also had the most `WATCH` results, so the forecast and the status agree there.
5. **Real seasons often score above 100%,** for example 135% to 191% for plot 9001 in 2023. Its baseline had only 4 past seasons, and these aren't known maize fields, so read this as rule behaviour only.

## Maize season check (model 0.6.0)

The "consistent with a maize season?" flag (`app/processing.py`, `season_check`) run on the same 4 demo plots and 3 seasons, with real Sentinel-2 data, the same assumed planting date (20 September) and `min_clear_pixels` 20. Each cell is the flag on that day after planting, with its reason and how many clear images it used. To repeat it, call `season_check` on `GeeSource` observations for these polygons; nothing is saved.

**The demo plots are placeholder locations, not known maize fields, and the planting date is assumed.** So this shows how the rule behaves on real images. It can't say whether any flag is right.

| Plot | Season | DAP 45 | DAP 70 | DAP 100 | DAP 150 | DAP 180 |
|---|---|---|---|---|---|---|
| 9001 | 2023 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 7 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 10 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 11 img) | INCONSISTENT (STILL_GREEN_AFTER_SEASON, 13 img) |
| 9001 | 2024 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 6 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 10 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 15 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 17 img) |
| 9001 | 2025 | INCONSISTENT (GREEN_AT_PLANTING, 4 img) | INCONSISTENT (GREEN_AT_PLANTING, 8 img) | INCONSISTENT (GREEN_AT_PLANTING, 11 img) | INCONSISTENT (GREEN_AT_PLANTING, 16 img) | INCONSISTENT (GREEN_AT_PLANTING, 19 img) |
| 9002 | 2023 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 8 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 11 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 12 img) | CONSISTENT (CONSISTENT_SO_FAR, 14 img) |
| 9002 | 2024 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 6 img) | CONSISTENT (CONSISTENT_SO_FAR, 9 img) | CONSISTENT (SEASON_PATTERN_SEEN, 15 img) | CONSISTENT (SEASON_PATTERN_SEEN, 17 img) |
| 9002 | 2025 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 7 img) | INCONSISTENT (NO_GROWTH_SEEN, 10 img) | CONSISTENT (CONSISTENT_SO_FAR, 15 img) | CONSISTENT (CONSISTENT_SO_FAR, 17 img) |
| 9003 | 2023 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 7 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 10 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 11 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 13 img) |
| 9003 | 2024 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 6 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 10 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 14 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 16 img) |
| 9003 | 2025 | INCONSISTENT (GREEN_AT_PLANTING, 4 img) | INCONSISTENT (GREEN_AT_PLANTING, 7 img) | INCONSISTENT (GREEN_AT_PLANTING, 10 img) | INCONSISTENT (GREEN_AT_PLANTING, 15 img) | INCONSISTENT (GREEN_AT_PLANTING, 17 img) |
| 9004 | 2023 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 7 img) | INCONSISTENT (NO_GROWTH_SEEN, 9 img) | INCONSISTENT (NO_GROWTH_SEEN, 10 img) | INCONSISTENT (NO_GROWTH_SEEN, 12 img) |
| 9004 | 2024 | NOT_ENOUGH_DATA (TOO_EARLY, 5 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 6 img) | INCONSISTENT (NO_GROWTH_SEEN, 9 img) | INCONSISTENT (NO_GROWTH_SEEN, 13 img) | INCONSISTENT (NO_GROWTH_SEEN, 15 img) |
| 9004 | 2025 | NOT_ENOUGH_DATA (TOO_EARLY, 4 img) | NOT_ENOUGH_DATA (PATTERN_UNCLEAR, 7 img) | INCONSISTENT (NO_GROWTH_SEEN, 10 img) | INCONSISTENT (NO_GROWTH_SEEN, 15 img) | INCONSISTENT (NO_GROWTH_SEEN, 17 img) |

What it shows:

1. **The rule rarely says `CONSISTENT` on these plots:** 3 of 12 plot-seasons by 180 DAP. Most results are `NOT_ENOUGH_DATA` with `PATTERN_UNCLEAR`: the plot never looks like bare soil in the first 30 days (NDVI above the 0.35 bare-soil limit), so the start of a maize season can't be confirmed. That fits places with grass, weeds or mixed cover, or a planting date that isn't 20 September.
2. **`GREEN_AT_PLANTING` appears on 9001 and 9003 in 2025** from the first check: the plots were already green when the field should have been bare. On a real plot, that is exactly what a person should look at (another crop, or a wrong planting date).
3. **`NO_GROWTH_SEEN` covers plot 9004 every season from 100 DAP.** That agrees with 9004 having the most `WATCH` and `AT_RISK` results and the lowest harvest ranges. It may not be a maize field at all.
4. **A flag can change as the season goes on.** Plot 9002 in 2025 was `INCONSISTENT` (`NO_GROWTH_SEEN`) at 100 DAP and `CONSISTENT` by 150 DAP: the rise came later than the assumed planting date allows for. Because `INCONSISTENT` opens a review flag that only a person closes, an early `INCONSISTENT` can create review work that a later fetch would have cleared.
5. **Before 70 DAP the rule gives `NOT_ENOUGH_DATA` (`TOO_EARLY`)** unless the plot is green at planting, as designed: maize growth isn't expected to show yet.
6. **The values are starting suggestions** (`SEASON_*` in `app/params.py`, TO_CONFIRM). Tuning them on placeholder plots would fit noise, so they weren't changed. They need real, field-confirmed maize plots (question 28).

## Results (model 0.6.0)

### Simulated sample

| Plot | Season | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA | Status changes |
|---|---|---|---|---|---|---|---|
| Simulated (normal) | 2023 | 30 | 26 (87%) | 4 (13%) | 0 (0%) | 0 (0%) | 4 |
| Simulated (normal) | 2024 | 30 | 20 (67%) | 5 (17%) | 0 (0%) | 5 (17%) | 5 |
| Simulated (stressed) | 2025 | 16 | 5 (31%) | 9 (56%) | 2 (12%) | 0 (0%) | 2 |

### Simulated sample, how soon stress shows

| Plot | Season | First WATCH (DAP) | First AT_RISK (DAP) |
|---|---|---|---|
| Simulated (normal) | 2023 | 40 | none |
| Simulated (normal) | 2024 | 40 | none |
| Simulated (stressed) | 2025 | 30 | 75 |

### Simulated sample, AT_RISK results

| Plot | Season | Date | DAP | Anomaly | Signals |
|---|---|---|---|---|---|
| Simulated (stressed) | 2025 | 2025-12-04 | 75 | -4.43 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, RAIN_BELOW_NORMAL |
| Simulated (stressed) | 2025 | 2025-12-09 | 80 | -8.53 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, RAIN_BELOW_NORMAL |

### Simulated sample, forecast at 80 DAP

| Plot | Season | Range (% of normal) | Width (points) |
|---|---|---|---|
| Simulated (normal) | 2023 | 97 to 111 | 13 |
| Simulated (normal) | 2024 | 96 to 109 | 13 |
| Simulated (stressed) | 2025 | 88 to 100 | 12 |

### Real data, forecast at 80 DAP

| Plot | Season | Range (% of normal) | Width (points) |
|---|---|---|---|
| 9001 | 2023 | 135 to 191 | 56 |
| 9001 | 2024 | 93 to 148 | 55 |
| 9001 | 2025 | 92 to 137 | 45 |
| 9002 | 2023 | 97 to 144 | 47 |
| 9002 | 2024 | 91 to 135 | 43 |
| 9002 | 2025 | 104 to 139 | 36 |
| 9003 | 2023 | 101 to 146 | 45 |
| 9003 | 2024 | 95 to 127 | 32 |
| 9003 | 2025 | 97 to 146 | 49 |
| 9004 | 2023 | 87 to 101 | 14 |
| 9004 | 2024 | 71 to 86 | 15 |
| 9004 | 2025 | 78 to 101 | 23 |

### Real data, by plot and season

| Plot | Season | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA | Status changes |
|---|---|---|---|---|---|---|---|
| 9001 | 2023 | 30 | 16 (53%) | 1 (3%) | 0 (0%) | 13 (43%) | 7 |
| 9001 | 2024 | 30 | 25 (83%) | 2 (7%) | 2 (7%) | 1 (3%) | 7 |
| 9001 | 2025 | 30 | 22 (73%) | 7 (23%) | 0 (0%) | 1 (3%) | 7 |
| 9002 | 2023 | 30 | 15 (50%) | 1 (3%) | 0 (0%) | 14 (47%) | 7 |
| 9002 | 2024 | 30 | 21 (70%) | 4 (13%) | 4 (13%) | 1 (3%) | 8 |
| 9002 | 2025 | 30 | 21 (70%) | 9 (30%) | 0 (0%) | 0 (0%) | 7 |
| 9003 | 2023 | 30 | 16 (53%) | 1 (3%) | 0 (0%) | 13 (43%) | 7 |
| 9003 | 2024 | 30 | 24 (80%) | 3 (10%) | 1 (3%) | 2 (7%) | 9 |
| 9003 | 2025 | 30 | 19 (63%) | 9 (30%) | 1 (3%) | 1 (3%) | 8 |
| 9004 | 2023 | 30 | 8 (27%) | 5 (17%) | 0 (0%) | 17 (57%) | 7 |
| 9004 | 2024 | 30 | 5 (17%) | 15 (50%) | 8 (27%) | 2 (7%) | 11 |
| 9004 | 2025 | 30 | 12 (40%) | 17 (57%) | 0 (0%) | 1 (3%) | 7 |

### Real data, likely causes

| Status | Likely cause | Results |
|---|---|---|
| AT_RISK | UNEXPLAINED | 16 |
| WATCH | UNEXPLAINED | 42 |
| WATCH | WATER_DEFICIT | 6 |
| WATCH | none | 26 |

### Real data, confidence

| Confidence | Results |
|---|---|
| HIGH | 0 (0%) |
| MEDIUM | 128 (36%) |
| LOW | 232 (64%) |

### Real data, why NOT_ENOUGH_DATA

| Reason | Stage | Results |
|---|---|---|
| NO_NORMAL_CURVE | After 30 DAP | 39 |
| NO_NORMAL_CURVE | Up to 30 DAP | 2 |
| NO_RECENT_READINGS | After 30 DAP | 22 |
| NO_RECENT_READINGS | Up to 30 DAP | 3 |

### Real data, estimated normal bands

| Results with a status | Using an estimated band |
|---|---|
| 294 | 51 (17%) |

### Real data, AT_RISK results

| Plot | Season | Date | DAP | Anomaly | Signals |
|---|---|---|---|---|---|
| 9001 | 2024 | 2025-02-02 | 135 | -1.83 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9001 | 2024 | 2025-02-07 | 140 | -1.83 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9002 | 2024 | 2025-02-02 | 135 | -5.45 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9002 | 2024 | 2025-02-07 | 140 | -9.27 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9002 | 2024 | 2025-02-12 | 145 | -11.18 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9002 | 2024 | 2025-02-17 | 150 | -5.06 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9003 | 2024 | 2024-12-09 | 80 | -3.60 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9003 | 2025 | 2026-01-18 | 120 | -1.94 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9004 | 2024 | 2024-10-05 | 15 | -4.14 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-10-10 | 20 | -4.14 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-11-24 | 65 | -2.25 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-11-29 | 70 | -2.25 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-12-04 | 75 | -2.25 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-12-09 | 80 | -2.25 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2025-02-02 | 135 | -1.85 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9004 | 2024 | 2025-02-07 | 140 | -1.85 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |

### Real data, if a setting changes

| Settings | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA |
|---|---|---|---|---|---|
| Team settings | 360 | 204 (57%) | 74 (21%) | 16 (4%) | 66 (18%) |
| `at_risk_anomaly` -1.25 | 360 | 204 (57%) | 69 (19%) | 21 (6%) | 66 (18%) |
| `at_risk_anomaly` -2 | 360 | 204 (57%) | 80 (22%) | 10 (3%) | 66 (18%) |
| `at_risk_min_days` 5 | 360 | 204 (57%) | 69 (19%) | 21 (6%) | 66 (18%) |
| `at_risk_min_days` 15 | 360 | 204 (57%) | 77 (21%) | 13 (4%) | 66 (18%) |
| `not_enough_data_window_days` 15 | 360 | 186 (52%) | 69 (19%) | 15 (4%) | 90 (25%) |
| `not_enough_data_window_days` 30 | 360 | 222 (62%) | 77 (21%) | 16 (4%) | 45 (12%) |
| `min_clear_pixels` 100 | 360 | 181 (50%) | 64 (18%) | 23 (6%) | 92 (26%) |
