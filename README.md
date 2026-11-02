# Rinda AI

Rinda ("protect" in Kinyarwanda) is an AI-powered parametric crop-insurance system for maize farmers in Rwanda. It is a final-year learning project: payouts are simulated, and an insurer officer makes every claim decision.

The full design is in [docs/RINDA_PROJECT.md](docs/RINDA_PROJECT.md), which is the source of truth. Unresolved items are in [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md), and departures from the design are in [docs/DECISIONS.md](docs/DECISIONS.md).

| Folder | Contents | Status |
|---|---|---|
| `backend/` | Spring Boot 3 REST API (Java 17) | Auth and plots working |
| `ml-service/` | Python FastAPI service and Earth Engine extraction | Not built yet |
| `android/` | Android app (Java + XML) | Android Studio template, for Agy |
| `dashboard/` | Next.js insurer dashboard | Placeholder, for Agy |
| `database/` | Local MySQL script | Working |
| `docs/` | Design, decisions, open questions | Working |
| `scripts/` | Setup helpers | Working |

## Setup on a fresh Windows computer

Run these from PowerShell. No admin rights are needed once the tools are installed.

### 1. Install the tools

| Tool | Version | Notes |
|---|---|---|
| Git | any recent | |
| JDK | 17 | Other Java versions can stay installed. The project uses JDK 17 explicitly. |
| MySQL Community Server | 8.0 | Only the server files are needed. Skip the configuration wizard's service if asked. |
| Python | 3.13 | |
| Node.js | 24 | Only for the dashboard. |

Maven does not need installing: the backend will include the Maven Wrapper.

XAMPP's "MySQL" is MariaDB, which does not support the `POLYGON SRID 4326` columns Rinda uses. It can stay installed, because Rinda uses its own MySQL 8 on port 3307.

### 2. Get the code

```powershell
git clone https://github.com/Irene-pedos/Rinda-AI.git
cd Rinda-AI
git config user.name "Your Name"
git config user.email "you@example.com"
```

Using `git config` without `--global` keeps your identity in this repo only, so a borrowed computer stays clean.

### 3. Create your local secrets

```powershell
powershell -ExecutionPolicy Bypass -File scripts\new-env.ps1
```

This creates `.env` with new random passwords and keys. It is ignored by Git, so each computer has its own. If your JDK or MySQL is somewhere else, change `JAVA_17_HOME` or `MYSQL_BIN` in `.env`.

### 4. Set up MySQL

Once per computer:

```powershell
powershell -ExecutionPolicy Bypass -File database\mysql-dev.ps1 -Action init
```

This creates a private MySQL 8 server in `%LOCALAPPDATA%\Rinda\mysql`, listening only on `127.0.0.1:3307`, with a `rinda` database and user.

**It does not start by itself after a reboot.** At the start of each session, run:

```powershell
powershell -ExecutionPolicy Bypass -File database\mysql-dev.ps1 -Action start
```

The script also accepts `-Action stop` and `-Action status`.

To reset the database completely, stop the server and delete `%LOCALAPPDATA%\Rinda\mysql`, then run `init` again.

### 5. Run the backend

MySQL must be running. The first run downloads Maven and the dependencies, which takes a few minutes.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Always use `scripts\mvn.ps1`: it runs Maven on JDK 17 whatever the system default is.

| What | Where |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Health check | http://localhost:8080/actuator/health |

The `dev` profile loads the demo data, turns on Swagger UI, and prints login codes in the backend log as `[DEV OTP] phone=... code=...`. Without it the backend refuses to start, because it has no way to send a code.

Demo accounts (log in with the code from the log):

| Phone | Role |
|---|---|
| +250780000001 | Farmer, with 3 covered plots |
| +250780000002 | Farmer, with 1 plot pending verification |
| +250780000003 | Agent |
| +250780000004 | Officer |
| +250780000005 | Admin |

Demo plots are placeholders until the team supplies real locations and planting dates.

### 6. Run the backend tests

MySQL must be running, because the tests use its spatial functions. They use a separate `rinda_test` database and never touch the demo data.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test "-Dtest=PlotApiTest"
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test "-Dtest=PlotApiTest#otherCropsAreRejected"
```

### Before returning a borrowed computer

Everything that matters must be pushed to GitHub. Then sign out of GitHub in Windows Credential Manager, and delete `.env` and `%LOCALAPPDATA%\Rinda`.
