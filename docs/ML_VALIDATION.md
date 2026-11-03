# ML validation

What has been tested, what the results show, and what they can't show. For how the rules work, see `RINDA_PROJECT.md` §3 and questions 15 to 18 in `OPEN_QUESTIONS.md`.

**There is no accuracy figure in this file, because there is no field ground truth yet.** This back-test counts how often each result appears. It can't say whether any result was correct.

## Back-test of the demo settings

Run on 2026-11-03 with model version 0.5.0, using the `CONFIRMED_FOR_DEMO` values in the `thresholds` table. To repeat it, run this from `ml-service` with the backend running:

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

## Effect of filling the gaps

| Real data, 360 results | Before | After |
|---|---|---|
| `HEALTHY` | 187 (52%) | 217 (60%) |
| `WATCH` | 54 (15%) | 62 (17%) |
| `AT_RISK` | 2 (1%) | 15 (4%) |
| `NOT_ENOUGH_DATA` | 117 (32%) | 66 (18%) |
| `NOT_ENOUGH_DATA` from gaps in the normal band | 92 | 41 |

The simulated results didn't change, because the sample has no gaps of this kind.

## What the results show

1. **Filling the gaps removed over half of the `NOT_ENOUGH_DATA` results, but 17% of statuses now rest on an estimated band.** That's 51 of 294, and all of them are `LOW` confidence.
2. **Most remaining `NOT_ENOUGH_DATA` is in the 2023 season:** 57 of 66. That season has only 4 past seasons, so its gaps are longer than 2 bins. Later seasons have more history, and were at 7% or lower.
3. **`AT_RISK` is still uncommon, but most of it doesn't look like crop stress.** Of the 15 results, only 3 fall in the sensitive stage of 50 to 100 DAP. Another 10 come at 120 DAP or later, when maize may be drying off or already harvested, so a small timing difference looks like a large drop. The last 2 are at 15 and 20 DAP, before the crop is established. A claim trigger limited to the sensitive stage would see only those 3.
4. **8 of the 15 `AT_RISK` results use an estimated band.** One of those is among the 3 in the sensitive stage. Question 19 asks whether an estimated band should be allowed to count towards a claim.
5. **The rules do pick up the simulated stress, but late.** Stress began at 55 DAP and `AT_RISK` first appeared at 75 DAP, 20 days later. The 2 normal simulated seasons never reached `AT_RISK` in 60 results. They did reach `WATCH` in 9 of those results, or 15%.
6. **`at_risk_anomaly` and `at_risk_min_days` both change `AT_RISK` now.** An anomaly limit of −2 halves it from 15 to 9, and −1.25 raises it to 18. A minimum of 15 days gives 10, and 5 days gives 20.
7. **Anomaly scores can be very large,** down to −11.88. Narrow normal bands may be the reason, so a score far below −1.5 doesn't always mean a severe drop.
8. **Raising `min_clear_pixels` to 100 raised `AT_RISK` from 15 to 25.** It also raised `NOT_ENOUGH_DATA` to 26%. The cause wasn't investigated. It may be that fewer readings change the baseline, so treat this with care.
9. **Status moves often.** Each 30-result season changed status 5 to 12 times on real data, and this count includes moves in and out of `NOT_ENOUGH_DATA`. So health alerts only go out when a plot gets worse than its last known status.
10. **Most results are low confidence.** 63% of real results were `LOW`, and none were `HIGH`, because `HIGH` isn't used before back-testing against field results.
11. **Most low NDVI has no explained cause.** Of the 50 real results with low NDVI, 45 were `UNEXPLAINED` and 5 were `WATER_DEFICIT`, so `needsFieldVisit` would come up often. Another 27 `WATCH` results came from low rain alone, with NDVI inside the band.

## Harvest forecast

The forecast is the plot's greenness total so far, divided by the normal band's high and low totals (§10.1). It's a percent of normal greenness, not a yield.

1. **It reacts weakly to stress late in the season.** At 80 DAP, the stressed simulated season gave 88% to 100%, and the two normal seasons gave 96% to 111% and 97% to 111%, so the ranges overlap. The stressed plot was well below normal for its last 3 periods, but its early periods were slightly above normal, and every period counts the same.
2. **Bare soil keeps every total high.** NDVI is around 0.2 even before the crop grows, and that part is counted in both the plot and its normal, which pulls every result towards 100%. A plot that failed completely after 55 DAP would still score well above 0%. Question 21 asks whether to change this.
3. **Real ranges are wide, as the spec expects (§3.6).** At 80 DAP they were 16 to 57 points wide. They're wide because past seasons varied a lot, and they weren't narrowed.
4. **Plot 9004 had the lowest ranges,** 74% to 93% in 2024. It also had the most `WATCH` results, so the forecast and the status agree there.
5. **Real seasons often score above 100%,** for example 132% to 187% for plot 9001 in 2023. Its baseline had only 4 past seasons, and these aren't known maize fields, so read this as rule behaviour only.

## Results

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
| 9001 | 2023 | 132 to 187 | 55 |
| 9001 | 2024 | 94 to 150 | 57 |
| 9001 | 2025 | 92 to 140 | 48 |
| 9002 | 2023 | 98 to 143 | 45 |
| 9002 | 2024 | 94 to 138 | 44 |
| 9002 | 2025 | 100 to 133 | 33 |
| 9003 | 2023 | 102 to 138 | 36 |
| 9003 | 2024 | 95 to 124 | 29 |
| 9003 | 2025 | 98 to 140 | 41 |
| 9004 | 2023 | 92 to 107 | 16 |
| 9004 | 2024 | 74 to 93 | 18 |
| 9004 | 2025 | 81 to 101 | 21 |

### Real data, by plot and season

| Plot | Season | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA | Status changes |
|---|---|---|---|---|---|---|---|
| 9001 | 2023 | 30 | 16 (53%) | 1 (3%) | 0 (0%) | 13 (43%) | 7 |
| 9001 | 2024 | 30 | 25 (83%) | 2 (7%) | 2 (7%) | 1 (3%) | 7 |
| 9001 | 2025 | 30 | 22 (73%) | 7 (23%) | 0 (0%) | 1 (3%) | 7 |
| 9002 | 2023 | 30 | 14 (47%) | 2 (7%) | 0 (0%) | 14 (47%) | 7 |
| 9002 | 2024 | 30 | 21 (70%) | 4 (13%) | 4 (13%) | 1 (3%) | 8 |
| 9002 | 2025 | 30 | 25 (83%) | 5 (17%) | 0 (0%) | 0 (0%) | 5 |
| 9003 | 2023 | 30 | 15 (50%) | 2 (7%) | 0 (0%) | 13 (43%) | 8 |
| 9003 | 2024 | 30 | 24 (80%) | 3 (10%) | 1 (3%) | 2 (7%) | 9 |
| 9003 | 2025 | 30 | 19 (63%) | 8 (27%) | 2 (7%) | 1 (3%) | 12 |
| 9004 | 2023 | 30 | 11 (37%) | 2 (7%) | 0 (0%) | 17 (57%) | 8 |
| 9004 | 2024 | 30 | 12 (40%) | 12 (40%) | 4 (13%) | 2 (7%) | 10 |
| 9004 | 2025 | 30 | 13 (43%) | 14 (47%) | 2 (7%) | 1 (3%) | 10 |

### Real data, likely causes

| Status | Likely cause | Results |
|---|---|---|
| AT_RISK | UNEXPLAINED | 15 |
| WATCH | UNEXPLAINED | 30 |
| WATCH | WATER_DEFICIT | 5 |
| WATCH | none | 27 |

### Real data, confidence

| Confidence | Results |
|---|---|
| HIGH | 0 (0%) |
| MEDIUM | 134 (37%) |
| LOW | 226 (63%) |

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
| 9002 | 2024 | 2025-02-02 | 135 | -5.38 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9002 | 2024 | 2025-02-07 | 140 | -10.58 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9002 | 2024 | 2025-02-12 | 145 | -11.88 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9002 | 2024 | 2025-02-17 | 150 | -4.20 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9003 | 2024 | 2024-12-09 | 80 | -4.07 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9003 | 2025 | 2026-01-18 | 120 | -2.13 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9003 | 2025 | 2026-01-28 | 130 | -2.12 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-10-05 | 15 | -3.24 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-10-10 | 20 | -3.24 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2025-02-02 | 135 | -1.88 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9004 | 2024 | 2025-02-07 | 140 | -1.88 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL, NORMAL_CURVE_ESTIMATED |
| 9004 | 2025 | 2025-11-14 | 55 | -8.88 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2025 | 2025-11-19 | 60 | -8.88 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |

### Real data, if a setting changes

| Settings | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA |
|---|---|---|---|---|---|
| Team settings | 360 | 217 (60%) | 62 (17%) | 15 (4%) | 66 (18%) |
| `at_risk_anomaly` -1.25 | 360 | 217 (60%) | 59 (16%) | 18 (5%) | 66 (18%) |
| `at_risk_anomaly` -2 | 360 | 217 (60%) | 68 (19%) | 9 (2%) | 66 (18%) |
| `at_risk_min_days` 5 | 360 | 217 (60%) | 57 (16%) | 20 (6%) | 66 (18%) |
| `at_risk_min_days` 15 | 360 | 217 (60%) | 67 (19%) | 10 (3%) | 66 (18%) |
| `not_enough_data_window_days` 15 | 360 | 196 (54%) | 59 (16%) | 15 (4%) | 90 (25%) |
| `not_enough_data_window_days` 30 | 360 | 236 (66%) | 64 (18%) | 15 (4%) | 45 (12%) |
| `min_clear_pixels` 100 | 360 | 182 (51%) | 61 (17%) | 25 (7%) | 92 (26%) |

