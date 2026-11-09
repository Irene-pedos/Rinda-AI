# Rinda demo runbook

How to start Rinda, check it works, and show it. For setting up a new computer, follow the [README](../README.md) first.

## 1. Before the demo

| When | What |
|---|---|
| Once per computer | README steps 1–8: tools, `.env`, MySQL, ML service set-up, `earthengine authenticate` |
| The day before | Run the whole demo once with `scripts\demo-check.ps1` (below) and fix anything that fails |
| The day before | Make sure the team's values are entered (section 5). Without them no claim can start. |
| Before you start | Phone charged, USB cable, laptop on power, screen mirroring tested if you show the phone on a projector |

## 2. Start everything

From the repo root, in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1
```

It starts MySQL (if needed), then opens two windows: **Rinda backend** (the login codes appear here as `[DEV OTP] phone=... code=...`) and **Rinda ML service**. If a phone is plugged in by USB, it also connects the phone to the backend. It waits until both services answer.

Then check the whole system:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-check.ps1
```

It logs in as every demo role, loads each main screen's data through the API, checks the security rules, and runs the simulated analysis on demo plot 9001. Every line should say `PASS`. An `INFO` line about trigger values means section 5 isn't done yet.

To stop: close the two windows. MySQL keeps running until the computer restarts (`database\mysql-dev.ps1 -Action stop` stops it).

## 3. Connect the phone

**USB (most reliable).** Plug the phone in with USB debugging on, then run `start-dev.ps1`, or on its own:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
```

The app's default server address (`http://127.0.0.1:8080/api/v1/`) then reaches the laptop. **Run this again every time you unplug or restart the phone.**

**Wi-Fi.** Laptop and phone on the same network. In the app: Profile → Server address (testing, debug builds only) → `http://<laptop IP>:8080/api/v1/`. Find the IP with `ipconfig`. Windows Firewall must allow port 8080, and many lab and campus networks block devices from reaching each other, so test it before the demo. This is question 11 in `OPEN_QUESTIONS.md`: local or cloud hosting is still open.

If the app says "No connection to Rinda" while the phone has internet, the backend isn't running or the USB link isn't set up. See the README troubleshooting note in step 5.

## 4. Demo accounts

Log in with the phone number; the 6-digit code appears in the **Rinda backend** window.

| Phone | Role | Use it to show |
|---|---|---|
| `0780000001` | Farmer 1 | 3 covered plots, crop health, forecast, alerts, claims, payout account |
| `0780000002` | Farmer 2 | A plot waiting for the agent's check |
| `0780000003` | Agent | My farmers, verify a plot, record a field visit |
| `0780000004` | Officer | Claims queue and case file, decisions (dashboard or API) |
| `0780000005` | Admin | Run analysis, thresholds, seasons, cover, payout rates, accounts, audit log |

Everything they show is labelled **Demo**. The demo plots are placeholder locations, so they only ever get the **simulated** sample data, never real satellite data (`DECISIONS.md`).

## 5. Values the team must supply

These drive money and decisions, so Rinda never invents them (`RINDA_PROJECT.md` §0). Enter them as the demo admin through the API (Swagger UI at http://localhost:8080/swagger-ui/index.html, "Admin" section). Every change is audited.

| Value | Endpoint | Without it |
|---|---|---|
| Trigger rule X (days AT_RISK), Y (forecast %), Z (rain deficit %) — question 3 | `PUT /admin/thresholds/trigger_at_risk_days`, `…/trigger_forecast_pct`, `…/trigger_rain_deficit_pct` with `status: "CONFIRMED_FOR_DEMO"` | **No claim ever starts.** The officer summary lists them under `triggerValuesMissing`. |
| Sum insured (and premium) per covered plot — question 4 | `PUT /admin/enrollments/{id}` | Approved claims stay `APPROVED`: there is no amount to pay. |
| Payout rate for a claim — question 4 | `PUT /admin/claims/{id}/payout-rate` | Same as above. |
| Season dates — question 5 | `PUT /admin/seasons/{id}` | Planting dates aren't checked against a season window. |

If the insurer can't answer in time, the spec allows **clearly labelled demo values** chosen by the team (§10.3). Use status `CONFIRMED_FOR_DEMO`, never `CONFIRMED`, and say so in the report.

## 6. Demo story

A suggested order. Each step names the account and what it proves.

1. **First launch (any new phone number).** Language, three onboarding slides, consent with a tick box, phone login, sign-up as a farmer. *Roles come from the account: sign-up only ever creates farmers.*
2. **Farmer 1: Home.** Farm summary, latest alert, rain, big shortcuts, Demo label.
3. **Farmer 1: Plot detail (Demo plot 1).** Crop health in plain words with "What you can do", confidence, the NDVI chart against the normal band, rain, a harvest forecast **range** labelled as a prototype, insurance cover. Tap the "?" buttons. *Every result shows its confidence; forecasts are ranges.*
4. **Farmer 1: Add a plot.** Draw it in 3 steps; it ends as "Waiting for check". *New plots are never monitored before verification.*
5. **Agent: verify the new plot,** correcting the planting date if needed. The farmer gets a "Plot verified" alert.
6. **Admin: Run analysis** (`POST /admin/analysis-runs`, `{"source":"SAMPLE","plotIds":[9001]}`), then show the run's per-plot result. *Real data only on real, verified plots; the sample only on demo plots; never a fallback.*
7. **Claim triggered** (needs section 5's trigger values, and a sample or real result that meets them). The farmer gets "A claim was started"; the claim is `UNDER_REVIEW`. *Claims start only from the satellite trigger; a farmer can't create one.*
8. **Farmer 1: Report a problem** with a photo. *It is evidence, attached to the claim; it never starts a claim or a payout.*
9. **Officer: queue and case file** (`GET /officer/claims`, `GET /officer/claims/{id}`): evidence, review flags, reports, field visits, history. Ask for a field visit; the agent records it.
10. **Officer: decide** with a written reason. Decline → the farmer reads why and asks for a review once → the officer answers. *A person decides every claim; the system never declines.*
11. **Payout (simulated).** Farmer 1 adds and verifies a mobile-money number (code in the backend window); the admin enters the insurer's payout rate; approval pays `sum insured × rate`, labelled simulated. *No real money moves.*
12. **Admin: audit log** (`GET /admin/audit-log`): every decision, threshold change and payout, with who and when.

## 7. What is simulated, and what to say

| Part | In the demo | Say |
|---|---|---|
| Login codes | Printed in the backend window, not sent by SMS | "Real SMS is future work." |
| Demo plots and their satellite data | Placeholder locations with simulated data | "Real plots get real Sentinel-2 and CHIRPS data through Earth Engine." |
| Payouts | A `PAID` record with a `SIM-…` reference | "No real money moves; mobile-money integration is future work." |
| Payout account name check | Recorded as `SIMULATED` | "Rinda can't see mobile-money accounts." |
| Harvest forecast | A prototype range from crop greenness | "Not yet compared with real harvests (`ML_VALIDATION.md`)." |
| Trigger values, rates | `CONFIRMED_FOR_DEMO` values from the team | "The insurer sets the real ones." |

## 8. If something goes wrong

| Symptom | Fix |
|---|---|
| App: "No connection to Rinda" | Backend window still running? Run the `adb reverse` command again. |
| Login code never appears | Look in the **Rinda backend** window, or `logs\backend-dev.log`. Wait 60 s before asking for another code. |
| Backend won't start | MySQL not running: `database\mysql-dev.ps1 -Action start`. Port 8080 busy: an old backend is still open. |
| Run analysis: `ML_SERVICE_ERROR` | ML service window closed, or Earth Engine not authorised (`.venv\Scripts\python -m app.gee_check`). |
| Run analysis: plot `SKIPPED` | The `errorCode` says why (`docs/API.md`, "Run analysis"). Demo plots only take `SAMPLE`. |
| No claim after analysis | Trigger values missing (section 5), or the data doesn't meet the rule. `GET /officer/summary` lists missing values. |
| Approved claim not paid | Missing sum insured or payout rate (section 5), or the farmer has no verified payout account. The case file shows which. |
