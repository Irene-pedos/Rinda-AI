# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state of this repo

`docs/RINDA_PROJECT.md` is the single source of truth for the product. Read it before writing feature code. If something is unclear, missing, or contradicts it, stop and ask the team rather than guessing, and log open questions in `docs/OPEN_QUESTIONS.md`. Departures from the spec go in `docs/DECISIONS.md`.

The repo root is the workspace, with `backend/`, `ml-service/`, `android/`, `dashboard/`, `database/`, `docs/` and `scripts/`. The root `README.md` has the Windows setup steps and shows what is built so far.

## Machine and tooling

The lab computer has no admin rights for this work, and its defaults are wrong for this project:

- **Java:** `java` on PATH is JDK 26. Spring Boot uses JDK 17 (`JAVA_17_HOME` in `.env`). Android's Gradle uses Android Studio's bundled JDK 21.
- **Database:** port 3306 and the `mysql` Windows service are XAMPP's MariaDB 10.4, not MySQL. Rinda's MySQL 8 is on port **3307**. Never point the app at 3306: MariaDB does not support `POLYGON SRID 4326` columns.
- **MySQL does not survive a reboot.** Start it with `powershell -ExecutionPolicy Bypass -File database\mysql-dev.ps1 -Action start`.
- **Secrets** are in `.env`, generated per computer by `scripts\new-env.ps1`.

## Backend

Run everything through `scripts\mvn.ps1`, which selects JDK 17. MySQL must be running, including for tests.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test "-Dtest=PlotApiTest#otherCropsAreRejected"
powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- **Tests use real MySQL** (`rinda_test`) through `ApiTestBase`, because H2 has no spatial functions.
- **Flyway owns the schema.** Add a new `V<n>__*.sql` migration; never edit an applied one. Demo data is the repeatable `db/demo/R__demo_seed.sql`, which only the `dev` profile loads.
- **Plot polygons** are stored as GeoJSON through a `@ColumnTransformer` using `ST_GeomFromGeoJSON`. SRID 4326 in MySQL is lat/lon order, so don't write WKT by hand.
- **Decision values** come from the `thresholds` table through `ThresholdService`. An empty result means "not supplied yet", never zero.
- **Errors** are `ProblemDetail` JSON with a stable `code`. Throw `ApiException`.
- In Windows PowerShell 5.1, quote native arguments that contain dots and start with a dash (for example `'--host=127.0.0.1'`), or they get split.

## ML service

Run from `ml-service/`. The Python is in `.venv` (3.13). Tests need neither Earth Engine nor the backend.

```powershell
.venv\Scripts\python -m pytest
.venv\Scripts\python -m pytest tests/test_processing.py::test_ndvi_formula
.venv\Scripts\python -m uvicorn app.main:app --port 8000
.venv\Scripts\python -m app.gee_check
```

- **Calculations go in `app/processing.py`**, with no Earth Engine dependency, so they stay testable on fixed data.
- **Settings go in `app/params.py`.** Change `MODEL_VERSION` when one changes.
- **Never fall back to sample data** when real data was requested. `samples/sample_plot.json` is simulated.
- **Windows Application Control blocks some compiled packages here** (pandas was blocked, so it isn't used). Never try to bypass it.
- `app.gee_check` tests the Earth Engine setup. Each computer needs its own `earthengine authenticate`.

## Android build

Run from `android/`. **The build currently fails** with `checkDebugAarMetadata`: `androidx.activity:1.13.0` and its transitive dependencies need `compileSdk` 36 or later, but `app/build.gradle.kts` sets 35. The `android-36` platform is installed. Until this is fixed, the commands below will not succeed. The JDK 26 failure shows only the message `26.0.2.1`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testDebugUnitTest --tests "com.example.rindaai.ExampleUnitTest"
.\gradlew.bat connectedDebugAndroidTest   # needs a device or emulator
.\gradlew.bat lintDebug
```

## Target architecture (spec §4)

```
Android app (Java + XML)  ─HTTPS/JSON─►  Spring Boot REST API  ─►  MySQL 8
Next.js dashboard         ─HTTPS/JSON─►        │
                                               └─HTTP─► Python FastAPI ML service ─► Google Earth Engine / CHIRPS / ERA5
```

- MySQL is the authority. SQLite on the phone is an offline copy plus an `outbox` queue. On sync the server wins, and claim and payout status always come from the server.
- Android only talks to the API, never to MySQL. The ML service is only called by the backend, using an internal API key.
- Spec ownership: Claude Code builds the database, backend and ML service. Agy builds the Android app and dashboard against that API (§0). Check who owns a piece of work before starting it.
- MVP scope is §10.1. It takes priority over the full system in §3–9 for what to build now; the rest stays documented as future work.

## Rules that must not be broken

These come from the spec (§0, §2.2, §3) and are not cut for the MVP.

- **Maize only.** Keep a `crop` column allowing only `MAIZE`. No crop pickers or multi-crop logic.
- **Never invent numbers that affect money or decisions** (thresholds, payout rates, premiums, sum insured, season dates). Put them in the `thresholds` config table as `TO_CONFIRM` and ask the team.
- **A human makes every final claim decision.** The system flags and recommends, and never auto-declines. Missing data goes to manual review, never to a decline.
- **Claims are created only by the parametric trigger.** A farmer's "Report a problem" is evidence and can request an agent visit, but never creates a claim or payout.
- **Plot health and claims are separate.** `AT_RISK` is not a claim. Health alerts route to plot detail and claim updates route to claim detail.
- **Every ML output carries a confidence and a data quality note.** Low confidence goes to human review. If data is insufficient, the status is `NOT_ENOUGH_DATA`; never guess one.
- **Never name a specific pest or disease as a cause.** Satellites cannot identify them.
- **Forecasts are ranges, never single tonnages.** Don't narrow a range artificially.
- **Report only metrics measured on held-out data.**
- **Every result stores `model_version`, input dates and parameters.** Claims store a frozen evidence snapshot. Claim decisions, threshold changes and model version changes go to `audit_log`.
- **Status changes need persistence** (for example 2+ observations or 10+ days), not one image.
- **Demo data is marked `is_demo = true`** and labelled "Demo" in every UI. Never mix it with real data.
- **No real money moves.** Payouts are simulated.
- **Roles come from the account.** Sign-up creates farmers only; admins create or approve agents, and farmers never see agent screens.

Where the Figma design conflicts with the spec, follow the Figma for look and feel and §2.2 for behaviour.

## Android conventions (spec §5.3, §9)

- One Activity per main flow, with Fragments for the tabs.
- All strings go in `strings.xml`: `values/` for English, `values-rw/` for Kinyarwanda.
- Colours and styles go in `res/values`, with dark variants in `values-night`.
- Touch targets are at least 48 dp, and status is never shown by colour alone.
- The JWT goes in EncryptedSharedPreferences, not SQLite. Never store OTPs or other users' data on the phone.
