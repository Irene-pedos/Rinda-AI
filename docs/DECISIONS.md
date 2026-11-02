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
| 2026-11-02 | The MVP processes one plot on request. `/process/batch`, `GET /baseline` and `POST /forecast` (§8) come later. | Day 2 adds the forecast and the "Run analysis" action, which will use them. |
