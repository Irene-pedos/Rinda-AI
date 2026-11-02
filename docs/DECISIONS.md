# Decisions log

Where the build departs from `RINDA_PROJECT.md`, or settles something it leaves open.

| Date | Decision | Why |
|---|---|---|
| 2026-11-02 | The repo root is the workspace, instead of a `rinda/` folder (§7). | The GitHub repo `Rinda-AI` is already the workspace, so an extra folder adds nothing. |
| 2026-11-02 | The Android Studio template moved from the repo root to `android/`. | Matches the §7 structure and gives Agy a starting point. |
| 2026-11-02 | The ML service uses Python 3.13, not 3.11 (§7). | The team's computer has 3.13, and all ML packages install on it. |
| 2026-11-02 | The dev database is MySQL 8 on port 3307, started by `database/mysql-dev.ps1`. | Port 3306 on the lab computer is XAMPP's MariaDB 10.4, which does not support `POLYGON SRID 4326` columns. No admin rights are available, so MySQL 8 cannot be a Windows service. |
| 2026-11-02 | The backend uses the Maven Wrapper. | Maven is not installed, and the wrapper works on a fresh computer. |
