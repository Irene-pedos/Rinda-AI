# 03 — Draft Data Model & API (Proposal)

This is a starting proposal derived from the discussion. Adjust names and types to the real codebase; keep the semantics.

## Portability note
The learning version uses MySQL/SQLite; the serious version uses Postgres. Store polygons as **GeoJSON text** in the learning version. In the serious version, migrate to PostGIS geometry columns. Keep geometry access in one place in the code so the switch is small.

## Tables

### `plots`
| Column | Notes |
|---|---|
| id | primary key |
| farmer_id | FK to the user/farmer |
| upi | string; validate the format; unique per active claim per season |
| verification_level | `SELF_DECLARED` / `DOCUMENT_SUPPORTED` / `FIELD_VERIFIED` / `NLA_VERIFIED` |
| status | `ACTIVE` / `FLAGGED` / `DISPUTED` / `REJECTED` |
| relationship_to_plot | `OWNER` / `FAMILY_MEMBER` / `TENANT` |
| crop | `MAIZE` for now |
| planting_date | date |
| season | season identifier (format to be decided) |
| boundary_geojson | walked polygon |
| planted_area_geojson | sub-polygon inside the boundary (nullable until declared) |
| walked_area_m2 | computed |
| document_area_m2 | extracted from the document (nullable) |
| area_diff_pct | computed |
| created_at, updated_at | timestamps |

### `plot_documents`
id, plot_id, type (`E_TITLE` / `PORTAL_SCREENSHOT` / `LEASE` / `OWNER_CONSENT`), storage_path, extracted_owner_name, extracted_upi, extracted_area_m2, extracted_land_use, uploaded_at. Restrict access; never log contents.

### `gps_walk_points`
id, plot_id, seq, lat, lng, accuracy_m, recorded_at, mock_location_flag.

### `plot_flags`
id, plot_id, type (`OUTSIDE_ADMIN_AREA` / `AREA_MISMATCH` / `OVERLAP` / `MOCK_LOCATION` / `SATELLITE_INCONSISTENT` / `OTHER`), details, created_at, resolved_at, resolved_by.

### `plot_verifications`
id, plot_id, verifier_id, method (`AGENT_VISIT` / `COOPERATIVE` / ...), geotagged_photo_path, result, verified_at.

### `satellite_observations`
id, plot_id, obs_date, ndvi_median, pixel_count, cloud_fraction, source, fetched_at.

### `rainfall_daily`
id, plot_id, date, rainfall_mm, source.

## Rules the backend must enforce
- No endpoint accepts a hand-drawn polygon. `boundary_geojson` comes only from walk points.
- Raising `verification_level` requires the checks for that level to have passed.
- Payout or other financial actions are rejected unless `verification_level >= FIELD_VERIFIED`.
- Overlapping claims set both plots `DISPUTED` and pause financial actions.
- One active claim per UPI per season.
- Satellite jobs are created only for plots with a saved `boundary_geojson`.

## Spring Boot endpoints (proposal)
- `POST /plots` create a plot claim (UPI, declaration)
- `POST /plots/{id}/documents` upload e-title / screenshot
- `POST /plots/{id}/walk` submit GPS walk points; server builds and validates the polygon
- `POST /plots/{id}/planted-area` set the planted sub-polygon (must lie inside the boundary)
- `GET /plots/{id}` plot with level, status, flags
- `POST /plots/{id}/verify` human verification (restricted role)
- `GET /plots/{id}/monitoring` combined satellite + rainfall + summary (proxied from the Python service)
- Dashboard (Next.js, insurer): list plots with level, status, flags, latest image date.

## Tests (use fake data only)
- Polygon built from walk points; area computed correctly.
- Hand-drawn polygon input is rejected.
- Overlap detection flags both plots.
- Area mismatch beyond the configured threshold creates a flag.
- Level gating blocks financial actions below `FIELD_VERIFIED`.
- Empty states: no plot, job running, no usable image, data available.
