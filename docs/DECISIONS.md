# Decisions log

Where the build departs from `RINDA_PROJECT.md`, or settles something it leaves open.

| Date | Decision | Why |
|---|---|---|
| 2026-11-02 | The repo root is the workspace, instead of a `rinda/` folder (§7). | The GitHub repo `Rinda-AI` is already the workspace, so an extra folder adds nothing. |
| 2026-11-02 | The Android Studio template moved from the repo root to `android/`. | Matches the §7 structure and gives Agy a starting point. |
| 2026-11-02 | The ML service uses Python 3.13, not 3.11 (§7). | The team's computer has 3.13, and all ML packages install on it. |
| 2026-11-02 | The dev database is MySQL 8 on port 3307, started by `database/mysql-dev.ps1`. | Port 3306 on the lab computer is XAMPP's MariaDB 10.4, which does not support `POLYGON SRID 4326` columns. No admin rights are available, so MySQL 8 cannot be a Windows service. |
| 2026-11-02 | The backend uses the Maven Wrapper, run through `scripts/mvn.ps1`. | Maven is not installed, and the wrapper works on a fresh computer. The script selects JDK 17, which is simpler than a per-computer Maven toolchains file. |
| 2026-11-02 | The GitHub repo is public. | Team choice. No secrets are committed; each computer generates its own `.env`. |
| 2026-11-02 | Spring Boot 3.5.16. | The latest 3.x release when the backend was created. |
| 2026-11-02 | MySQL's spatial functions handle plot polygons, instead of Hibernate Spatial (§7). | `ST_GeomFromGeoJSON` handles the lat/lon axis order of SRID 4326, and `ST_Area` gives geodesic area with no extra library. Tests confirm both. |
| 2026-11-02 | Backend tests run on real MySQL (`rinda_test`), not H2. | H2 lacks the spatial functions. Docker is not installed, so Testcontainers is not an option. |
| 2026-11-02 | "Inside Rwanda" (§5.2) is a bounding-box check. | Good enough for the MVP. It also accepts border areas of neighbouring countries, which the agent's verification must catch. |
| 2026-11-02 | Officers and admins log in with phone OTP in the MVP, not passwords (§5.3). | §10.1 lists phone login for all roles. Passwords are not built, so BCrypt is only used for OTP codes. |
| 2026-11-02 | New phone numbers sign up through `POST /auth/otp/verify`, and cooperatives are listed at `GET /auth/cooperatives`. | §8 has no sign-up endpoint, and the sign-up screen needs the cooperative list before login. |
| 2026-11-02 | Deleting a plot sets it to `ARCHIVED` instead of removing the row. | Past claims and observations must keep their plot as a legal record. |
| 2026-11-02 | `plots.planting_date` is nullable, and `plots` has a `data_status` column. | The API always requires a planting date, but the seeded placeholder plots must not contain an invented one. `TO_CONFIRM` marks those plots. |
| 2026-11-02 | The `seasons` table has `data_status`, with nullable dates. | Season dates are unconfirmed. The planting-date window is enforced only for `CONFIRMED` seasons. |
| 2026-11-02 | Added tables `otp_codes` and `refresh_tokens`, which §6 does not list. | Login needs them. Both store hashes, never the codes or tokens. |
| 2026-11-02 | Earth Engine project: `rinda-ai`. | The team's registered project. The ID is not a secret, but it stays in `.env` so each computer can set its own. |
| 2026-11-02 | The ML service doesn't use pandas or scikit-learn (§7). | Windows Application Control on the lab computer blocked a pandas DLL. A rolling median doesn't need pandas, and the MVP has no trained model. The policy was left in place. |
| 2026-11-02 | Added `data_origin` to `observations`, `weather_daily` and `baselines`, and an `ml_runs` table. | Keeps sample and real data apart on every row (§0 rule 4), and stores each run's dates and parameters so results can be reproduced (§3). |
| 2026-11-02 | The backend stores sample results on demo plots only, and real results only on `ACTIVE`, `CONFIRMED` plots. | Enforced in the backend, so no ML bug can mix the two. |
| 2026-11-02 | `POST /process/plot` never falls back to the sample file. | A caller asking for real data must get real data or an error. |
| 2026-11-02 | `docs/API.md` separates built endpoints from planned ones, whose fields are drafts. | Agy can plan against the full API without mistaking drafts for a finished contract. |
| 2026-11-02 | The backend has no CORS setup; the dashboard calls it through Next.js rewrites or server routes. | The MVP needs no browser access from another origin, and this avoids choosing allowed origins before the demo setup is known (question 11). |
| 2026-11-02 | The ML service applies the status rules, and reads their values from the backend's `thresholds` table on each run. | Calculations stay testable in `processing.py`, the backend stays the only home for decision values, and each run stores the values it used. |
| 2026-11-02 | Team decisions on questions 15 to 17 are in the `thresholds` table as `CONFIRMED_FOR_DEMO`, a new status that isn't final. | They can change without a code change. Each change goes to `audit_log`, and each `plot_status` row stores the values it used. |
| 2026-11-02 | The clear pixel limit moved from `params.py` to the `thresholds` table. | It decides what counts as a valid observation, so it has to change with the other decision values, and two copies could disagree. |
| 2026-11-02 | A flat normal band, where p50 equals p10, gives `NOT_ENOUGH_DATA`. | The anomaly can't be calculated, so calling the plot healthy would be a guess. |
| 2026-11-02 | The back-test can read real satellite data for the placeholder demo plots, but saves nothing. | The backend correctly refuses to store real results on unconfirmed plots. A test that saves nothing can still show how the rules behave. |
| 2026-11-02 | Gaps of up to 2 baseline bins are filled from the bins on each side (`BASELINE_MAX_FILLED_BINS`). Filled bins are marked, and a status using one is `LOW` confidence. | Question 18. A 3-bin gap means a straight line across 40 days, which could cut across the greenness peak. The ends of the curve are never extended. |
| 2026-11-02 | The harvest range is the plot's NDVI total divided by the normal band's p90 and p10 totals, over the 10-day periods that have both. | §10.1 asks for an NDVI integral against the plot's normal. Using the band's edges means the width comes from past seasons, not from a chosen number. |
| 2026-11-02 | Forecast confidence stays `LOW`. | It has never been compared with a real harvest, and it measures greenness, not yield. |
| 2026-11-02 | `harvest_forecasts` can hold a row with no range, plus the reason. | The newest row is then always current, so an old range can't be shown after the data runs out. |
| 2026-11-02 | `GET /plots/{id}/enrollment` returns `NOT_ENROLLED` when there is no row. | §2.2 #5 lists it as a status the farmer must see. |
| 2026-11-03 | A health alert is sent only when a plot gets worse than its last known status. | The back-test shows the status changing 5 to 12 times a season, and an alert for each would bury the ones that matter. |
| 2026-11-03 | Notifications carry a `code`, the plot id and `is_demo`, with English text as a fallback. | The app can show translated text and open the right screen, and demo alerts are labelled. |
| 2026-11-03 | The first result from a new model version is written to `audit_log`. | Model version changes must be audited (§0). |
| 2026-11-02 | Status confidence stops at `MEDIUM`, and `UNEXPLAINED` is always `LOW`. | The spec gives no confidence rule. Nothing is back-tested, so `HIGH` would claim more than is known (§3). |
| 2026-11-02 | The rain comparison ends on the latest day with CHIRPS data, and needs 30 complete days and 3 past seasons. Otherwise `rainPct` is null. | CHIRPS lags behind real time, and a missing day must not count as a dry day. |
| 2026-11-02 | Low rain means below `watch_rain_pct` for the water deficit cause (§3.5 says "below normal"). | §10.1 says "low rain", and using the same threshold avoids inventing a second number. |
| 2026-11-02 | Moving out of `AT_RISK` has no extra rule beyond the rolling median. | The median over 3 readings already stops one good image from breaking a low run. A longer rule would need a number the spec doesn't give. The back-test shows the status changes often, so this may need another look (`ML_VALIDATION.md`). |
| 2026-11-02 | The MVP processes one plot on request. `/process/batch`, `GET /baseline` and `POST /forecast` (§8) come later. | Day 2 adds the forecast and the "Run analysis" action, which will use them. |
| 2026-11-03 | Claude Code redesigned the Android UI and UX, which §0 gives to Agy. Agy continues from `docs/ANDROID_UI_GUIDELINES.md`. | Team request: the app was hard to use for first-time smartphone users, and Agy needs one design system to follow. |
| 2026-11-03 | Home shows the farm summary and the latest real alert, not a crop health status. | The old card always said "Healthy", even with no data (§0, §3: never guess a status). Health per plot is on Plot detail. |
| 2026-11-03 | "Ask for a review" changes nothing on the phone. It reports success only when the server accepts it, then reloads the claim. | Claim status always comes from the server. The old screen marked the claim `REVIEW_REQUESTED` and showed success even when the request failed. `POST /claims/{id}/review-request` is still a draft, so it fails until the backend builds it. |
| 2026-11-03 | New strings are in English only, and `MissingTranslation` is a lint warning (`android/app/lint.xml`). | Kinyarwanda review is cut for the MVP (§10.1) and question 12 is open. The app falls back to English, and the lint report lists every string to translate. |
| 2026-11-03 | The server address setting appears only in debug builds. | It is a testing tool, and a farmer could break their app by changing it. |
| 2026-11-03 | Claim detail shows "evidence not available" and no season code when the server gives none. | The old screen showed made-up evidence text and the season "2026A", which no data supplied (§0 rule 2). |
