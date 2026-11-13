# Rinda AI — Plot Registration, Verification & Satellite Monitoring (Handoff)

Date: 2026-11-11
Scope: how a farmer registers the plot they planted, how we check it, and how we monitor it from satellite data.
Crop scope for now: **maize only** (other crops later).

## How to use these files (for Claude Code)
Read in this order:
1. `00-README.md` (this file): decisions, constraints, open questions
2. `01-plot-verification.md`: farmer flow, verification levels, anti-fraud checks
3. `02-satellite-monitoring.md`: satellite pipeline, empty states, data sources
4. `03-data-model-and-api.md`: draft schema and endpoints (a proposal, not final)
5. `04-nla-roadmap-and-pitch.md`: what we cannot do now and the plan to get NLA data

Rules for implementation:
- Do **not** invent facts. Anything under "Open questions" is undecided: ask the user or make it configurable, don't assume.
- Do **not** scrape NLA / Irembo / Amakuru / Land Dashboard pages. Do not try to call NLA databases.
- Never commit real NIDs, UPIs, or farmer data to the repo, logs, tests, or fixtures. Use fake data in tests.
- Never show satellite output as "live". Always display the date of the latest image used.

## Problem we are solving
- Rwanda's land systems (UPI + NID) can show a plot's location, size and type, but **not what the farmer planted**.
- Rinda's earlier design let farmers **freely draw** their plot on a map. This is unacceptable: a farmer could draw someone else's plot.
- Real plot and crop data is the foundation of the whole system (including the insurer dashboard).

## Key decisions (agreed in discussion)
1. **No NLA database connection for now.** Access needs authorization and a long process. We will present the project first, then approach NLA for data. The system must work without NLA and be upgradeable to NLA later.
2. **Remove free-drawing of plots.** The plot polygon comes from a **GPS boundary walk** done at the plot, not from drawing on a map.
3. **Farmer provides plot proof themselves:** enters their UPI and uploads their e-title, or a screenshot of the plot details from the official portal, using the farmer's own access and consent.
4. **Verification levels** are stored on every plot: `SELF_DECLARED`, `DOCUMENT_SUPPORTED`, `FIELD_VERIFIED`, `NLA_VERIFIED`. The UI and the insurer dashboard show the level.
5. **Payouts or other real financial actions require `FIELD_VERIFIED` or higher.** `DOCUMENT_SUPPORTED` is for onboarding and demos only.
6. **A GPS walk proves presence at a place, not ownership of the plot.** Walking a neighbour's plot cannot be fully detected without NLA's parcel polygon. We mitigate with layered checks and human verification (see file 01).
7. **Satellite data checks the farmer's declaration; it does not identify the crop.** Satellite is near-real-time (days), never live.
8. **Satellite monitoring starts only after a plot is registered.** On registration, backfill imagery from the planting date to today, then refresh weekly.

## Known stack (from earlier project decisions; confirm before relying on it)
- Learning version: Android app (Java + XML), MySQL, plus SQLite; deadline about 5 days.
- Serious version: Java Spring Boot backend, Python ML service, Next.js + shadcn + Tailwind insurer dashboard, Postgres.
- The Python service framework (FastAPI?) has **not been confirmed**. Ask the user.
- Database choices differ between the learning and serious versions, so the schema in file 03 keeps geometry portable (see there).

## Open questions (ask the user; do not assume)
1. Is the Python service FastAPI?
2. Do administrative boundary files (province/district/sector/cell) exist in a usable open form? The UPI-vs-location coarse check depends on cell-level boundaries.
3. Does the e-title / official lookup show a plot sketch or coordinates? If yes, shape comparison is possible; if no, only area comparison.
4. Exact thresholds for area mismatch, GPS accuracy, and minimum usable satellite pixels. Values in these files are **starting suggestions to tune with real tests**.
5. Who performs `FIELD_VERIFIED` checks (cooperative agent, sector agronomist, other)?
6. How are tenants / relatives proven (owner OTP, signed note, lease)? Not yet decided.
7. Data protection: Law No. 058/2021 is believed to apply to personal data (NID, location). This must be verified with a reliable source before launch.

## Facts we verified (and where they came from)
- UPI is composed of codes for province, district, sector and cell, plus a parcel number (NLA FAQ: https://www.lands.rw/updates/faqs).
- `amakuru.lands.rw` is titled "LIIP — Land Information Inquiry Portal", requires login, and mentions ownership details and land classifications. It does not describe any public API.
- `rwandalanddashboard.lands.rw` renders with JavaScript; the public view appears to show aggregate figures (transactions, parcel sizes, land prices). Its per-plot capability was **not confirmed**.
- NLA's land-use and mapping services are stated to be reserved to government institutions (https://www.lands.rw/land-use-and-mapping).
- Other systems integrate with NLA (a FAO-indexed paper says the Ubutaka app integrates with Irembo, LAIS and NIDA), so access exists for approved partners. No public API was found.
- NLA general contact (lands.rw/home): phone 2142, Nyarugenge Pension Plaza, Kigali.
