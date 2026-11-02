# ML validation

What has been tested, what the results show, and what they can't show. For how the rules work, see `RINDA_PROJECT.md` §3 and questions 15 to 18 in `OPEN_QUESTIONS.md`.

**There is no accuracy figure in this file, because there is no field ground truth yet.** This back-test counts how often each result appears. It can't say whether any result was correct.

## Back-test of the demo settings

Run on 2026-11-02 with model version 0.3.0, using the `CONFIRMED_FOR_DEMO` values in the `thresholds` table. To repeat it, run this from `ml-service` with the backend running:

```powershell
.venv\Scripts\python -m app.backtest
```

It saves nothing to the database.

### How it was run

- **Replay:** the rules ran every 5 days through each season, up to 150 days after planting (DAP). Each result used only the data available on that date, and each baseline used only earlier years.
- **Real data:** Sentinel-2 and CHIRPS for the 4 demo plots, seasons 2023 to 2025, 360 results in total.
- **Simulated data:** two normal seasons and one with stress built in from 55 DAP. The stressed season stops at 81 DAP.

### Limits

- **These aren't known maize fields.** The demo plots are placeholder locations, so the counts show how the rules behave, not how maize behaved.
- **The planting date is assumed.** Every season uses 20 September, because the demo plots have no confirmed date (question 1).
- **The plots aren't independent.** All four are within about 1.2 km of each other, and likely share one CHIRPS rain pixel.
- **No season is a known drought or a known good year** (question 10), so false alarms and missed events can't be measured.

## What the results show

1. **Most `NOT_ENOUGH_DATA` comes from gaps in the normal curve, not from clouds.** On real data, 32% of results were `NOT_ENOUGH_DATA`. Of those 117, 92 (79%) were `NO_NORMAL_CURVE`: the growth stage had too few past readings to build a band. Only 25 had no recent clear reading. A longer window only moves this a little: 30 days gives 27% instead of 32%. Question 18 asks how to close the gaps.
2. **`AT_RISK` is rare, and the only real cases look like early-season noise.** It appeared in 2 of 360 real results, both on plot 9004 at 15 and 20 DAP. That's well before the sensitive stage of 50 to 100 DAP, and it had no rain signal. A claim trigger limited to the sensitive stage would ignore both.
3. **The rules do pick up the simulated stress, but late.** Stress began at 55 DAP and `AT_RISK` first appeared at 75 DAP, 20 days later. The 2 normal simulated seasons never reached `AT_RISK` in 60 results. They did reach `WATCH` in 9 of those results, or 15%.
4. **`at_risk_min_days` is the setting that changes `AT_RISK` most clearly.** A value of 5 gives 8 results and 15 gives none.
5. **`at_risk_anomaly` made no difference between −1.25 and −2.** The 2 real `AT_RISK` results were both at −3.24, far past all three values, so this range couldn't change them. Narrow normal bands may be why the scores are so large.
6. **Raising `min_clear_pixels` to 100 raised `AT_RISK` from 2 to 9.** It also raised `NOT_ENOUGH_DATA` to 42%. The cause wasn't investigated. It may be that fewer readings change the baseline, so treat this with care.
7. **Status moves often.** Each 30-result season changed status 7 to 14 times on real data, and this count includes moves in and out of `NOT_ENOUGH_DATA`. A farmer could see several status changes in a season, which matters for health alerts.
8. **Most results are low confidence.** 63% of real results were `LOW`, and none were `HIGH`, because `HIGH` isn't used before back-testing against field results.
9. **Most low NDVI has no explained cause.** Of the 35 real results with low NDVI, 32 were `UNEXPLAINED` and 3 were `WATER_DEFICIT`, so `needsFieldVisit` would come up often. Another 21 `WATCH` results came from low rain alone, with NDVI inside the band.

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

### Real data, by plot and season

| Plot | Season | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA | Status changes |
|---|---|---|---|---|---|---|---|
| 9001 | 2023 | 30 | 16 (53%) | 1 (3%) | 0 (0%) | 13 (43%) | 7 |
| 9001 | 2024 | 30 | 19 (63%) | 1 (3%) | 0 (0%) | 10 (33%) | 7 |
| 9001 | 2025 | 30 | 19 (63%) | 5 (17%) | 0 (0%) | 6 (20%) | 9 |
| 9002 | 2023 | 30 | 10 (33%) | 2 (7%) | 0 (0%) | 18 (60%) | 9 |
| 9002 | 2024 | 30 | 16 (53%) | 5 (17%) | 0 (0%) | 9 (30%) | 9 |
| 9002 | 2025 | 30 | 22 (73%) | 3 (10%) | 0 (0%) | 5 (17%) | 7 |
| 9003 | 2023 | 30 | 15 (50%) | 2 (7%) | 0 (0%) | 13 (43%) | 8 |
| 9003 | 2024 | 30 | 20 (67%) | 2 (7%) | 0 (0%) | 8 (27%) | 8 |
| 9003 | 2025 | 30 | 17 (57%) | 9 (30%) | 0 (0%) | 4 (13%) | 14 |
| 9004 | 2023 | 30 | 11 (37%) | 2 (7%) | 0 (0%) | 17 (57%) | 8 |
| 9004 | 2024 | 30 | 9 (30%) | 12 (40%) | 2 (7%) | 7 (23%) | 11 |
| 9004 | 2025 | 30 | 13 (43%) | 10 (33%) | 0 (0%) | 7 (23%) | 14 |

### Real data, likely causes

| Status | Likely cause | Results |
|---|---|---|
| AT_RISK | UNEXPLAINED | 2 |
| WATCH | UNEXPLAINED | 30 |
| WATCH | WATER_DEFICIT | 3 |
| WATCH | none | 21 |

### Real data, confidence

| Confidence | Results |
|---|---|
| HIGH | 0 (0%) |
| MEDIUM | 134 (37%) |
| LOW | 226 (63%) |

### Real data, why NOT_ENOUGH_DATA

| Reason | Stage | Results |
|---|---|---|
| NO_NORMAL_CURVE | After 30 DAP | 86 |
| NO_NORMAL_CURVE | Up to 30 DAP | 6 |
| NO_RECENT_READINGS | After 30 DAP | 22 |
| NO_RECENT_READINGS | Up to 30 DAP | 3 |

### Real data, AT_RISK results

| Plot | Season | Date | DAP | Anomaly | Signals |
|---|---|---|---|---|---|
| 9004 | 2024 | 2024-10-05 | 15 | -3.24 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |
| 9004 | 2024 | 2024-10-10 | 20 | -3.24 | NDVI_BELOW_NORMAL, NDVI_WELL_BELOW_NORMAL |

### Real data, if a setting changes

| Settings | Results | HEALTHY | WATCH | AT_RISK | NOT_ENOUGH_DATA |
|---|---|---|---|---|---|
| Team settings | 360 | 187 (52%) | 54 (15%) | 2 (1%) | 117 (32%) |
| `at_risk_anomaly` -1.25 | 360 | 187 (52%) | 54 (15%) | 2 (1%) | 117 (32%) |
| `at_risk_anomaly` -2 | 360 | 187 (52%) | 54 (15%) | 2 (1%) | 117 (32%) |
| `at_risk_min_days` 5 | 360 | 187 (52%) | 48 (13%) | 8 (2%) | 117 (32%) |
| `at_risk_min_days` 15 | 360 | 187 (52%) | 56 (16%) | 0 (0%) | 117 (32%) |
| `not_enough_data_window_days` 15 | 360 | 166 (46%) | 51 (14%) | 2 (1%) | 141 (39%) |
| `not_enough_data_window_days` 30 | 360 | 206 (57%) | 56 (16%) | 2 (1%) | 96 (27%) |
| `min_clear_pixels` 100 | 360 | 146 (41%) | 53 (15%) | 9 (2%) | 152 (42%) |

