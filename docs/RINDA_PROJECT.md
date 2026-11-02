# Rinda AI — Project Guide (Learning Version)

> **Read this whole file before writing any code.**
> This file is the single source of truth for building Rinda.
> **If anything is unclear, missing or contradicts this file, STOP and ask the team. Do not guess.** Guessing in the AI/insurance logic can make farmers lose money or trust, and that is the worst failure this project can have.

---

## 0. Instructions for the AI coding assistants

| Who | Does what |
|---|---|
| **Claude Code** (first) | Sets up the whole workspace. Builds the **database (MySQL)**, the **backend (Java Spring Boot)** and the **ML service (Python)**. Seeds demo data. Writes API docs. |
| **Agy** (second) | Builds the **Android app (Java + XML)** against the backend API. Then builds the **insurer web dashboard (Next.js + shadcn/ui + Tailwind)**, fixes remaining issues and polishes UI/UX. |

Rules for both:
1. **Maize only.** Do not build multi-crop logic, crop pickers or crop lists. Keep the code easy to extend later (a `crop` column with only `MAIZE` allowed), but nothing else.
2. **Never invent numbers that affect money or decisions.** These include thresholds, payout rates, premiums, sum insured and season dates. Put them in config tables marked `TO_CONFIRM` and ask the team.
3. **A human always makes the final claim decision.** The system can only flag and recommend. It must **never auto-decline** a claim.
4. **Demo data must be marked as demo** (`is_demo = true`) and shown with a "Demo" label in every UI. Never mix demo and real data.
5. **No real money moves.** Payouts are simulated.
6. **Commit small and often** to Git with clear messages. The team works on borrowed computers, so everything must live on GitHub, and setup must be re-runnable from the README.
7. When unsure, **ask**. List open questions in `docs/OPEN_QUESTIONS.md` and keep working on parts that don't depend on them.

---

## 1. What Rinda is

**Rinda** ("protect" in Kinyarwanda) is an AI-powered **parametric crop-insurance system for maize farmers in Rwanda**.

It watches every registered maize plot from space with free satellite data (Sentinel-1 radar, Sentinel-2 optical/NDVI) plus rainfall and soil-moisture data. It compares each plot with what is normal for maize at the same stage of the season and does three things:

1. **Detects crop stress early** and tells the farmer the likely cause in simple words.
2. **Predicts the harvest** as a range compared with a normal season (new feature).
3. **Triggers insurance claims automatically** when a plot's loss passes agreed thresholds. An **insurer officer reviews** each claim, and if it's approved, a (simulated) **mobile-money payout** is sent.

Every decision is **explained** to the farmer, and the farmer can **ask for a review**.

### Users
| User | Uses | Main jobs |
|---|---|---|
| **Farmer** | Android app | Register plots, see crop health, alerts and harvest forecast, track claims, add a payout account, ask for reviews, report problems |
| **Cooperative agent** | Android app (agent role) | See farmers in their cooperative, do field visits to confirm stress (ground truth), help farmers register |
| **Insurer officer** | Web dashboard | Review triggered claims with evidence, approve/decline with a written reason, answer review requests, see maps and reports |
| **Admin** | Web dashboard | Manage cooperatives, approve agents, set seasons and thresholds, manage model versions |

### Problem it solves
- Claims depend on slow, expensive field visits, so money arrives too late.
- Farmers don't understand why claims are approved or refused, so they don't trust insurance.
- Droughts and heavy rain are rising, and smallholder maize farmers have little protection.

---

## 2. How Rinda works (the core logic) — read carefully

### 2.1 Main flow
```
Farmer signs up (phone + OTP)
  → Registers maize plot (draws boundary on map) + planting date
  → Plot is VERIFIED (agent or admin checks it) → status PENDING_VERIFICATION → ACTIVE
  → Plot is ENROLLED in a season's cover (via cooperative/insurer) → covered
  → Every 5 days: ML service pulls satellite + rain data for each active plot
  → Computes NDVI etc., compares with the normal maize curve for that stage
  → Sets plot status: HEALTHY / WATCH / AT_RISK (+ likely stress cause + confidence)
  → Updates harvest forecast (after enough weeks of data)
  → If an insured plot passes the TRIGGER rule → a CLAIM is created automatically (status: UNDER_REVIEW)
  → Insurer officer reviews evidence (+ agent field visit if needed) → APPROVED / DECLINED (with reason)
  → If approved → simulated MoMo/Airtel payout → PAID
  → Farmer sees every step + plain-language explanation; can ask for a review once per decision
```

### 2.2 Mistakes in the current Figma design that change how the system works
The Figma screens were made before the logic was finalised. **Build the logic below, not what those screens imply.**

| # | In the Figma design | Correct behaviour |
|---|---|---|
| 1 | **"Report a loss" looks like the way to start a claim.** | Claims are created **automatically by the trigger** (that is what makes it parametric). A farmer's report is a **"Report a problem"**: it is saved as **evidence**. It can ask for an **agent visit**, and it is attached to an existing claim if there is one. It never creates a payout by itself. Rename the screen to **"Report a problem"**. |
| 2 | Crop pickers, "Beans" plots, mixed crops. | **Maize only.** Remove the crop picker. Show "Maize" as a fixed label. |
| 3 | Any farmer can open **Agent mode** from Profile, and Sign-up lets users choose "Agent". | Role comes from the account. Agents are **created or approved by an admin** and linked to a cooperative. Farmers never see agent screens. Sign-up creates **farmers only**. |
| 4 | Plot becomes active right after registration. | New plots are **PENDING_VERIFICATION** until an agent or admin confirms the boundary. The system also checks area, overlaps and minimum size (see 5.2). Only ACTIVE and ENROLLED plots can trigger claims. |
| 5 | Coverage screen with no way to get covered. | Add the **enrollment status** per plot per season: Not enrolled / Pending / Covered (dates). Enrollment is done by the cooperative/insurer, not paid in-app. In the app, the farmer **requests** enrollment. |
| 6 | Report reasons include **pests, disease and hail** as if insured. | What is covered is decided by the insurer (`TO_CONFIRM`). Satellites **cannot** identify a specific pest, disease or hail. These reasons go to **advice + agent visit**, not to an automatic payout. The Coverage screen must list only confirmed covered perils. |
| 7 | A plot "At risk" alert opens "Why this decision" like a claim decision. | Separate the two. **Plot health alerts** (WATCH / AT_RISK) go to **Plot detail / Crop stress**. **Claim updates** go to **Claim detail / Why this decision**. "At risk" is not a claim. |
| 8 | The growth chart is unlabeled "growth". | Label it **Crop health (NDVI)**, against the normal band for maize at the same **days after planting**. |
| 9 | No harvest forecast; no stress cause. | Add **Harvest forecast** and **Crop stress detail** (likely cause + confidence + "needs field visit" when unknown). |
| 10 | Planting date entered once, never checked. | The planting date drives every comparison. Ask for it at registration, let the agent confirm or correct it, and show a warning if the satellite green-up doesn't match. |
| 11 | "Delete my data" anytime. | If a plot has an active cover or an open claim, explain that deletion will happen after the season or claim closes (legal record). Otherwise delete. |
| 12 | Payout account is any number. | The MoMo/Airtel number must be **verified with OTP**, and the account name should match the farmer's name (simulated check). Changing it during an open claim needs re-verification. |
| 13 | "Read aloud" in Kinyarwanda. | Android has **no built-in Kinyarwanda text-to-speech**. Use pre-recorded audio for key messages, or English TTS only. **Ask the team.** |
| 14 | No "not enough data" state. | If clouds plus missing radar leave too few observations, show **"Not enough satellite data yet"**. Never guess a status. Never decline because data is missing; send the plot to manual review. |

---

## 3. The AI / ML part (the most critical part)

**Golden rules**
- **Explainable first.** Start with transparent rules and statistics that an officer and a farmer can understand. Add ML models only where we have data to train **and test** them.
- **Honest outputs.** Every output has a **confidence** (High/Medium/Low) and a **data quality** note. Low confidence → human review, never an automatic action.
- **No fake accuracy.** Report only metrics measured on held-out data. If there is no ground truth yet, say so.
- **Version everything.** Each result stores `model_version`, input data dates and parameters, so any decision can be reproduced and explained later.
- **Persistence, not one bad image.** A status changes only after the signal persists (for example 2+ consecutive observations or 10+ days), so one cloudy or noisy image doesn't cause an alert.

### 3.1 Data sources (all free)
| Data | Source | Use |
|---|---|---|
| Sentinel-2 L2A (10 m, ~5-day revisit) | Google Earth Engine `COPERNICUS/S2_SR_HARMONIZED` | NDVI, NDRE, NDMI |
| Sentinel-2 cloud mask | GEE `COPERNICUS/S2_CLOUD_PROBABILITY` (s2cloudless) + SCL band | Remove cloudy pixels |
| Sentinel-1 GRD radar (sees through clouds) | GEE `COPERNICUS/S1_GRD` | VV, VH, VH/VV for gaps and flood detection |
| Rainfall | CHIRPS daily (`UCSB-CHG/CHIRPS/DAILY`) | Rain vs normal |
| Soil moisture | ERA5-Land (`ECMWF/ERA5_LAND/DAILY_AGGR`) or SMAP | Dry/wet signal |
| Historical maize yields | NISR Seasonal Agricultural Survey (district level) | Harvest model calibration |
| Ground truth | **Agent field visits in Rinda** (collected over time) | Labels for stress model, validation |

GEE needs a **free non-commercial/academic project registration**. A backup is the Copernicus Data Space Ecosystem (free).

### 3.2 Plot-level processing
1. Plot polygon (WGS84) → shrink it inward by **10 m** (removes mixed edge pixels).
2. Require at least **N clear pixels** (`TO_CONFIRM`, suggested 20). Below that → "not enough data". A 0.1 ha plot is only ~10 Sentinel-2 pixels, so **very small plots are unreliable**. Set a minimum plot size (`TO_CONFIRM`, suggested 0.1 ha) and tell the farmer.
3. For each clear Sentinel-2 date compute the plot means:
   - **NDVI** = (B8 − B4)/(B8 + B4)
   - **NDRE** = (B8 − B5)/(B8 + B5)
   - **NDMI** = (B8 − B11)/(B8 + B11)
4. Sentinel-1: mean VV, VH (dB) and VH/VV per date.
5. Gap-fill NDVI when it's cloudy, using a radar-based estimate. **Mark gap-filled values as `source = S1_ESTIMATE` with lower confidence.**
6. Smooth the time series (Savitzky–Golay or a rolling median) before comparing.
7. Store every raw and smoothed value in `observations`.

### 3.3 Normal maize curve (baseline)
- Index by **days after planting (DAP)**, not calendar date.
- Build the normal band from **past seasons (2018 onward)** of maize areas in the same district or agro-ecological zone. Use median plus the 10th–90th percentiles per DAP bin (e.g. 10-day bins).
- Needs reference maize areas. **Ask the team** for known maize locations; candidates are cooperative plots, or ESA WorldCereal / Digital Earth Africa crop masks if available.
- Store baselines in `baselines` with district, season type (A/B), DAP bin, p10, p50, p90 and version.

### 3.4 Plot health status (HEALTHY / WATCH / AT_RISK)
Compute an **anomaly score** = (plot NDVI − p50) / (p50 − p10) for the current DAP, plus the rain deficit (% of normal rain over the last 30 days).

Starting rule (thresholds are `TO_CONFIRM`, in the `thresholds` config table):
- **HEALTHY:** NDVI ≥ p10 band edge.
- **WATCH:** below the edge for ≥ 1 observation, **or** rain < 60% of normal over 30 days.
- **AT_RISK:** below the edge for ≥ 2 consecutive observations / ≥ 10 days **and** clearly below (e.g. anomaly ≤ −1).
- **NOT_ENOUGH_DATA:** too few valid observations in the last 15 days.

### 3.5 Likely stress cause (rules first, ML later)
| Cause | Signals |
|---|---|
| **Water deficit (drought)** | NDVI below normal + rain < normal + NDMI low + soil moisture low |
| **Excess water / flooding** | Sudden S1 VV drop (standing water) + very high rain |
| **Possible nutrient problem** (low confidence) | NDRE drops before NDVI while rain and moisture are normal |
| **Unexplained stress** | NDVI below normal while weather is normal and neighbouring plots are fine → **request agent visit** (could be pests, disease, poor management) |

- Output: the cause, a confidence and the list of signals used (for the explanation screen).
- **Never say "pest X" or "disease Y".** Satellites can't tell that.
- After enough agent field visits are collected (labels), train a **Random Forest** classifier. Compare it with the rules on held-out data, and switch only if it's clearly better.

### 3.6 Harvest forecast (new feature)
- Model: regression (start with **Ridge regression**, try Random Forest) predicting **yield as % of the district's normal**. Features:
  - seasonal NDVI integral up to now
  - peak NDVI
  - rain total
  - soil-moisture deficit
  - DAP
- Training: district × season data from NISR yields and the satellite features of maize areas.
- Validation: **leave-one-season-out**. Report MAE.
- Output a **range** (e.g. 70–85% of normal), never a single tonnage. Show it only after **~6 weeks after planting** (`TO_CONFIRM`), with "This is an estimate".
- With few data points the honest range will be wide. That is correct; don't narrow it artificially.

### 3.7 Claim trigger (parametric)
- An **insured, ACTIVE** plot creates a claim when the **trigger rule** is met. Suggested starting rule (`TO_CONFIRM` with the insurer):
  - AT_RISK for ≥ X days during a sensitive stage (flowering/grain filling ≈ 50–100 DAP)
  - **and** forecast yield < Y% of normal **or** rain deficit < Z%.
- Payout amount = sum insured × payout rate for the loss level. **Rates come from the insurer config. Never invent them.**
- The claim stores a **frozen evidence snapshot**: values, charts data, model versions, thresholds used.
- Status flow: `UNDER_REVIEW → (FIELD_VISIT_REQUESTED) → APPROVED | DECLINED → PAID | REVIEW_REQUESTED → FINAL`.
- Declines need a written reason. The farmer can request **one review** per decision.

### 3.8 Validating the system before showing it
- Back-test the status and trigger rules on known bad seasons. **Ask the team** for confirmed drought or flood seasons and districts; for example, Eastern Province droughts are widely reported but must be confirmed.
- Report false alarms and missed events (**basis risk**) honestly in the project report.
- Keep a **demo mode** with clearly labelled simulated plots for the presentation.

---

## 4. Architecture

```
Android app (Java + XML)  ──HTTPS/JSON──►  Spring Boot REST API  ──►  MySQL 8
Next.js dashboard (web)   ──HTTPS/JSON──►        │
                                                 └──HTTP──► Python ML service (FastAPI)
                                                              └──► Google Earth Engine / CHIRPS / ERA5
Scheduler (Spring @Scheduled, every 5 days) → asks ML service to process all active plots → saves results → creates alerts/claims
```
- **Two databases, two jobs:** **MySQL** on the server is the single source of truth for everyone. **SQLite** on each phone stores that user's own data offline (last plot status, charts data, alerts, claims) and a queue of unsent reports and field visits.
- SQLite is a **copy**, never the authority: on sync, server data wins, and claim/payout status is always taken from the server.
- The Android app **never** connects to MySQL directly. It always goes through the API.
- The ML service has no direct user access. Only the backend calls it, with an internal API key.

---

## 5. Requirements

### 5.1 Functional requirements
**Farmer app**
1. Sign up / log in with phone + OTP. In dev mode the OTP is printed in the backend log (no SMS cost).
2. Choose language (Kinyarwanda / English) and theme (light/dark); give consent.
3. Register a maize plot:
   - draw the boundary on a map, or walk the edge with GPS
   - enter the planting date
   - the plot is then PENDING_VERIFICATION
4. My plots: list with status chip (icon + word + colour).
5. Plot detail:
   - status and explanation
   - NDVI chart vs normal band
   - rain chart
   - likely cause
   - harvest forecast range
   - check history
   - enrollment status
6. Alerts: plot health alerts and claim updates (separate types).
7. Claims: list, detail with tracker and evidence, "Why this decision", ask for review.
8. Report a problem: plot, what you see, optional photo, request agent visit.
9. Payout account (MoMo/Airtel, OTP-verified, simulated).
10. Profile: edit profile, notification settings, low-data mode, SMS alerts, privacy (see/delete data), help, log out.
11. Offline: cache last data in **SQLite** (with "Last updated" time shown). Queue problem reports and field visits in SQLite and send them when online (WorkManager). Show sync status (Pending / Sent).

**Agent (same app, agent role)**
1. My farmers list with status filters.
2. Verify plots (confirm boundary and planting date).
3. Field visit:
   - checklist
   - plant condition
   - observed cause
   - growth stage
   - photo with GPS + time
   - saved offline, synced later
4. Help register a farmer.

**Insurer dashboard (web)**
1. Log in (officer/admin roles).
2. Claims queue → claim detail with:
   - evidence snapshot
   - NDVI and rain charts
   - map
   - field visits
   - approve/decline with a reason
3. Review requests.
4. Map of plots by status; reports by district.
5. Admin pages:
   - cooperatives, agents, seasons, enrollment
   - thresholds config, model versions
   - audit log

### 5.2 Validation rules (backend)
- Plot area between min and max (`TO_CONFIRM`). The polygon must be valid (not self-intersecting) and inside Rwanda.
- Plots must not overlap other farmers' plots by more than 10% (`TO_CONFIRM`). An overlap flags the plot for agent check (fraud prevention).
- The planting date must fall within the season window (Season A / B dates `TO_CONFIRM`).
- One phone number = one account.

### 5.3 Non-functional requirements
- **Security:**
  - JWT auth, roles (FARMER, AGENT, OFFICER, ADMIN)
  - BCrypt for any passwords (dashboard)
  - HTTPS in production
  - input validation
  - no secrets in Git (`.env`)
- **Privacy:**
  - consent recorded with date
  - farmer can see and delete data (with the rule in §2.2 #11)
  - photos stored privately
- **Auditability:** every claim decision, threshold change and model version change is written to `audit_log`.
- **Low bandwidth:** paginated APIs; compressed images; maps load only on tap; SMS fallback (simulated or sandbox).
- **Accessibility:**
  - big text and touch targets ≥ 48 dp
  - status never shown by colour alone
  - all strings in `strings.xml` (`values/` English, `values-rw/` Kinyarwanda)
- **Reliability:** the ML job is idempotent (re-running gives the same result); failures are logged and retried.

---

## 6. Database (MySQL 8) — main tables
Use **Flyway** migrations. Spatial columns use `POLYGON SRID 4326`. Also store GeoJSON text for easy transfer.

| Table | Key columns |
|---|---|
| `users` | id, phone (unique), full_name, role, language, status, created_at |
| `consents` | user_id, version, accepted_at |
| `cooperatives` | id, name, district, sector |
| `agents` | user_id, cooperative_id, approved_by |
| `farmers` | user_id, cooperative_id, national_id_last4 (optional) |
| `seasons` | id, code (2026A), type (A/B), start_date, end_date |
| `plots` | id, farmer_id, name, crop (`MAIZE`), boundary POLYGON, geojson, area_ha, planting_date, status (PENDING_VERIFICATION/ACTIVE/REJECTED/ARCHIVED), verified_by, is_demo |
| `enrollments` | plot_id, season_id, status, sum_insured, premium, start/end |
| `observations` | plot_id, date, source (S2/S1/S1_ESTIMATE), ndvi, ndre, ndmi, vv, vh, clear_pixels, cloud_pct, smoothed_ndvi |
| `weather_daily` | plot_id, date, rain_mm, soil_moisture |
| `baselines` | district, season_type, dap_bin, p10, p50, p90, version |
| `plot_status` | plot_id, date, status, anomaly, rain_pct, cause, confidence, explanation_json, model_version |
| `harvest_forecasts` | plot_id, date, low_pct, high_pct, model_version, confidence |
| `thresholds` | key, value, status (TO_CONFIRM/CONFIRMED), updated_by |
| `claims` | id, plot_id, season_id, trigger_date, status, payout_rate, amount, evidence_snapshot_json, model_version |
| `claim_decisions` | claim_id, officer_id, decision, reason, decided_at |
| `review_requests` | claim_id, farmer_message, status, answer |
| `problem_reports` | plot_id, farmer_id, reason, note, photo_url, agent_visit_requested, claim_id (nullable) |
| `field_visits` | plot_id, agent_id, checklist_json, condition, observed_cause, growth_stage, photo_url, lat, lng, visited_at |
| `payout_accounts` | user_id, provider, msisdn, verified, name_match |
| `payouts` | claim_id, account_id, amount, status, reference, is_simulated (always true) |
| `notifications` | user_id, type (HEALTH/CLAIM/SYSTEM), title, body, link, read |
| `model_versions` | name, version, params_json, metrics_json, created_at |
| `audit_log` | actor_id, action, entity, entity_id, before_json, after_json, at |

---

## 7. Free tools and setup

| Area | Tool (free) |
|---|---|
| Android | **Android Studio** (latest), **JDK 17**, Java + XML, Material Components |
| Android libraries | Retrofit + Gson (API), OkHttp, **SQLite** (built into Android, local database on the phone — use Room, which is a SQLite layer, or plain `SQLiteOpenHelper` if the lecturer prefers), **WorkManager** (sync), **osmdroid** (OpenStreetMap maps, no API key), **MPAndroidChart** (charts), Glide (images), Fused Location (GPS) |
| Backend | **Spring Boot 3** (Web, Security, Data JPA, Validation), Maven, **Flyway**, Hibernate Spatial, JWT (jjwt), springdoc-openapi (Swagger UI) |
| Database | **SQLite** (on the phone, offline) + **MySQL 8 Community** (server) + MySQL Workbench (or XAMPP's MySQL if needed — check it supports spatial SRID) |
| ML service | **Python 3.11**, FastAPI, `earthengine-api`, pandas, numpy, scipy, scikit-learn, joblib |
| Satellite | **Google Earth Engine** (free academic registration), backup Copernicus Data Space |
| Dashboard | **Next.js**, TypeScript, **shadcn/ui**, Tailwind CSS, Recharts, Leaflet (OpenStreetMap) |
| SMS (optional) | Africa's Talking **sandbox** (free) or log-only in dev |
| Payments | Simulated. Optionally the MTN MoMo developer **sandbox** (free) |
| Other | Git + GitHub, Postman, VS Code, Figma (design reference) |

### Workspace structure (Claude Code creates this)
```
rinda/
├── README.md               # setup steps that work on a fresh borrowed computer
├── docs/                   # this file, API docs, OPEN_QUESTIONS.md, decisions log
├── backend/                # Spring Boot (Java 17, Maven)
├── ml-service/             # Python FastAPI + GEE scripts + notebooks
├── android/                # Android Studio project (Java + XML)
├── dashboard/              # Next.js insurer dashboard
├── database/               # seed scripts, ER diagram
└── .env.example            # all secrets as placeholders
```

---

## 8. API overview (backend)
Base: `/api/v1`. All endpoints need a JWT except auth.

- **Auth:** `POST /auth/otp/request`, `POST /auth/otp/verify`, `POST /auth/refresh`
- **Me:** `GET/PUT /me`, `POST /me/consent`, `DELETE /me` (deletion request)
- **Plots:** `GET /plots`, `POST /plots`, `GET /plots/{id}`, `PUT /plots/{id}`, `DELETE /plots/{id}`
- **Plot data:**
  - `GET /plots/{id}/observations?from&to`
  - `GET /plots/{id}/status`
  - `GET /plots/{id}/forecast`
  - `GET /plots/{id}/weather`
  - `GET /plots/{id}/baseline`
- **Enrollment:** `POST /plots/{id}/enrollment-request`, `GET /plots/{id}/enrollment`
- **Alerts:** `GET /notifications`, `POST /notifications/{id}/read`
- **Claims:** `GET /claims`, `GET /claims/{id}`, `POST /claims/{id}/review-request`
- **Reports:** `POST /problem-reports` (multipart photo)
- **Payout:**
  - `GET/POST /payout-account`
  - `POST /payout-account/verify`
- **Agent:**
  - `GET /agent/farmers`
  - `POST /agent/plots/{id}/verify`
  - `POST /agent/field-visits`
- **Officer/Admin (dashboard):**
  - claims queue
  - `POST /claims/{id}/decision`
  - review answers
  - thresholds, seasons, cooperatives, agents, enrollments
  - audit log, map data
- **Internal (ML only):** `POST /internal/ml/results` (API key)

**ML service:**
- `POST /process/plot`
- `POST /process/batch`
- `GET /baseline`
- `POST /forecast`
- `GET /health`

---

## 9. Android app screens (Java + XML)

**Local storage:** SQLite (see §4). Keep the SQLite schema a small mirror of the server tables the user needs, plus an `outbox` table (id, type, payload_json, created_at, status, retries). Never store OTPs or other users' data on the phone; store the JWT in EncryptedSharedPreferences, not in SQLite.
Follow the Figma design for **look and feel**, but follow §2 for **behaviour**.

1. **Onboarding:** Splash, Language & theme, Onboarding (3 slides), Consent.
2. **Account:** Login (phone), Verify OTP, Sign up (farmer only), About you.
3. **Main tabs:**
   - Home (logo, plot summary, latest alert, harvest forecast tile, weather tile)
   - Plots
   - Alerts
   - Claims
   - Profile
4. **Plots:**
   - Register plot (map/GPS → planting date → review → "Pending verification")
   - Plot detail
   - Crop stress detail
   - Harvest forecast
   - Weather & rain
   - Health map
   - Edit plot
5. **Claims and money:** Claim detail (tracker + evidence), Why this decision, Ask for review (bottom sheet), Payout account (+ OTP verify), Coverage/Enrollment.
6. **Other farmer screens:** Report a problem (4 steps), Notification settings, Edit profile, Help & privacy.
7. **States:** Offline, Not enough satellite data, Empty states, Error, Log-out and delete confirmations.
8. **Agent:** My farmers, Verify plot, Field visit (+ success).

Use one Activity per main flow plus Fragments for the tabs. Store the colours/sizes/styles from the design system in `res/values` (light and `values-night` dark).

---

## 10. Development process — 3-day sprint (+2 buffer days)

**We have 3 days (5 at most).** Build the **MVP below first**. Sections 3–9 describe the full system: build them only as far as the MVP needs, and keep the rest as documented "next steps". **Never cut the safety rules in §0 and §3** (human decides, confidence shown, no auto-decline, demo labelled, no invented money numbers).

### 10.1 MVP scope (must work for the demo)
| Keep (MVP) | Simplify | Cut for now (document as future work) |
|---|---|---|
| Phone login (OTP printed in backend log), farmer + agent + officer roles | Sign-up = name + phone + cooperative (seeded list) | Real SMS, dark mode, read-aloud, Kinyarwanda review (keep `values-rw` with drafts) |
| Register maize plot (draw on osmdroid map) + planting date | Verification = agent taps "Verify" | Overlap/fraud checks beyond a simple area check |
| Real satellite pipeline for **3–5 demo plots** in one district: S2 NDVI + cloud mask, CHIRPS rain | **Baseline = same plot's past seasons (2019+)**: p10/p50/p90 by days after planting. No regional reference maize needed | Sentinel-1 gap-fill, NDRE/NDMI, soil moisture (show "coming soon"), health map |
| Plot status HEALTHY / WATCH / AT_RISK / NOT_ENOUGH_DATA with explanation + confidence | Likely cause: **only 2 rules**: "Water deficit" (low NDVI + low rain) and "Unexplained → agent visit" | Nutrient/flood causes, ML stress classifier (needs field labels) |
| Plot detail: NDVI vs normal band chart + rain chart | Harvest forecast = NDVI-integral vs the plot's normal, shown as a **wide range**, labelled "Prototype estimate" | Trained yield model with NISR data (future, needs data) |
| Trigger → auto claim (UNDER_REVIEW) with evidence snapshot | Enrollment seeded as "Covered" for demo plots | Enrollment request flow |
| Officer approves/declines with reason (simple Next.js page: claims list + claim detail) | Payout = simulated "PAID" record + farmer notification | MoMo sandbox, maps and reports in the dashboard, admin config UI (edit thresholds in the DB) |
| Farmer: alerts, claims list/detail, why-this-decision, ask for review | Report a problem = text + optional photo, saved as evidence | **SQLite** cache of plots, status, alerts and claims + offline queue for reports and field visits (required by the lecturer) |
| Agent: my farmers list + simple field visit form | | |

**Pipeline shortcut (allowed and honest):** the ML service runs the GEE extraction **once per demo plot** and stores the results in MySQL. The scheduler can be a manual "Run analysis" admin button. The data is real; only the scheduling is manual.

### 10.2 Day-by-day plan
| Day | Claude Code (backend + ML) | Agy (frontend) |
|---|---|---|
| **Day 1** | Repo + README + `.env.example`; MySQL schema (Flyway) with the MVP tables; Spring Boot auth (OTP dev, JWT, roles); plots CRUD; seed cooperatives/users/demo plots; Swagger. **ML:** GEE auth, NDVI + CHIRPS extraction for demo plots, baseline from past seasons, results saved through `/internal/ml/results` | Android project, theme/colours/strings from design, Splash → Language → Consent → Login/OTP → Home shell with bottom tabs, Retrofit + JWT storage |
| **Day 2** | Status + cause rules, harvest range, trigger → claim + evidence snapshot, claim decision + review request + simulated payout, notifications, agent endpoints, "Run analysis" endpoint | SQLite local DB (tables: plots, plot_status, observations, notifications, claims, outbox) + sync; plots list, register plot (map + planting date), plot detail (MPAndroidChart: NDVI vs band, rain), alerts, claims list/detail, why-this-decision, ask for review |
| **Day 3** | API tests for auth/roles/claims; ML sanity checks (formulas, no-data case); `docs/ML_VALIDATION.md` (what was tested, known limits); help Agy wire the dashboard | Report a problem, payout account, profile, agent screens; **Next.js dashboard** (login, claims queue, claim detail with charts, approve/decline); full demo run on a phone |
| **Day 4–5 (buffer)** | Bug fixes; optional Sentinel-1 gap-fill | UI/UX polish, Kinyarwanda strings, empty/error/offline states, rehearse demo |

### 10.3 To unblock Day 1, the team must provide (otherwise placeholders marked TO_CONFIRM)
1. **District + 3–5 real maize plot locations** (boundaries or GPS points) and their **planting dates**.
2. **Google Earth Engine** account approved (register today; approval can take time).
3. **Trigger thresholds** to use for the demo. If the insurer can't answer in time, use clearly labelled demo values and state this in the report.

**Definition of done for each feature:**
- works end to end on a real phone or emulator
- has error and empty states
- strings in `strings.xml`
- committed to GitHub

---

## 11. Testing
- **Backend:** JUnit + MockMvc for every endpoint; test role access (a farmer can't see other farmers' plots).
- **ML:**
  - unit tests for index formulas and rules
  - a fixed sample dataset with known expected outputs
  - back-test report in `docs/ML_VALIDATION.md`
- **Android:** manual test checklist per screen; test offline mode, slow network, small screens (360 dp) and dark mode.
- **End-to-end demo scenario** (seeded, labelled Demo):
  1. a farmer with 3 maize plots: one healthy, one watch, one at risk
  2. the at-risk plot triggers a claim
  3. an agent visits
  4. an officer approves
  5. a simulated payout is shown

---

## 12. Open questions (ask the team — do not guess)
1. Insurer rules: covered perils, sum insured per hectare, premium/subsidy, payout rates, trigger thresholds.
2. Season A and B exact date windows for maize in our target district(s).
3. Which district(s) and cooperative do we pilot in? Do we have real plot boundaries or need to create demo ones?
4. Reference maize areas for the baselines, and any confirmed past drought/flood seasons for back-testing.
5. Minimum plot size and maximum allowed overlap.
6. Kinyarwanda read-aloud: pre-recorded audio, or drop the feature for now?
7. Who verifies plots in the pilot: agents only, or admins too?
8. Hosting for the demo: local only (laptop + phone on the same Wi-Fi), or a free cloud host?
9. Who reviews and corrects the Kinyarwanda text?

---

## 13. Later (serious version)
After this learning version, rebuild with:
- **React Native (Expo)** for the app
- **Next.js** for the web
- **FastAPI**
- **PostgreSQL + PostGIS**

Keep the same database design, API contracts and ML logic so the work carries over. Expand to other crops (beans, rice, potatoes) only after maize results are validated.
