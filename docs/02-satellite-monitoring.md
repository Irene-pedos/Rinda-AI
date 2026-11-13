# 02 — Satellite Monitoring

## Principles
- Satellite data **checks** the farmer's declaration (crop, planting date); it does **not** identify the crop. Maize and sorghum look similar; maize with beans (intercropping) mixes pixels; plots are often smaller than a few Sentinel-2 pixels.
- It is **not live**. Show the date of the latest usable image everywhere. A weekly update is a realistic promise.
- Monitoring starts only once a plot has a saved polygon. Before that, the screen shows an empty state, not fake data.

## Flow
1. Plot registered and polygon saved (file 01) -> create a satellite job.
2. **Backfill:** fetch past imagery from the declared planting date to today (the archive goes back years, so a new plot can show its season so far on day one).
3. **Weekly refresh:** a scheduled job fetches new images and rainfall for every active plot.
4. Store raw observations; compute the display values from them.
5. App and insurer dashboard show: NDVI curve, rainfall, latest image date, and a "consistent with declared crop/season?" flag.

## UI states
- No plot registered: "Register your plot to start monitoring" (button to registration).
- Plot registered, job running: "Fetching satellite data".
- Plot registered, no usable image yet: "No clear image since [date]; showing rainfall only" (expected during cloudy weeks).
- Data available: curve + rainfall + latest image date + flag.
- Never label anything "live" or "real-time".

## Processing rules for small plots
- Use only pixels **inside** the polygon; drop edge pixels that touch the boundary.
- Record how many pixels were used and show it, so weak signals are visible. Set a minimum pixel count below which the flag shows "not enough data" (value to tune).
- Mask clouds and shadows with Sentinel-2's scene classification layer before computing NDVI.
- Aggregate with the **median** over usable pixels, not the mean.
- Store raw values per observation: date, NDVI median, pixel count, cloud fraction, source. This lets logic improve later without re-fetching.

## "Consistent with maize season?" flag (rule-based for now)
- Compare the NDVI time series with the expected pattern after the declared planting date: low (bare soil), then rising growth, then a peak, then decline toward harvest.
- Outcomes: `CONSISTENT`, `INCONSISTENT` (e.g. bare all season, or long-lived green cover), `NOT_ENOUGH_DATA`.
- This is a **review flag**, not proof. Do not auto-reject plots on it.
- Do **not** train a crop classifier now. Instead keep collecting labelled data (farmer declarations, geotagged photos, agent confirmations) so a Rwanda-specific classifier can be trained after a few seasons.

## Data sources (free for development)
- Optical: Sentinel-2 (about 10 m pixels; about 5-day revisit; image availability after about a day). Access options: Microsoft Planetary Computer, Google Earth Engine, Copernicus Data Space.
- Radar (for cloudy periods): Sentinel-1 (about 6–12 day revisit).
- Rainfall: CHIRPS or Open-Meteo (coarse, a few km).
- Soil type/pH/organic carbon: SoilGrids (about 250 m, an estimate, not a measurement of the plot).
- **Check each source's licence for commercial use before launch** (e.g. Earth Engine's free tier is for non-commercial use).
- The resolution and revisit figures above are from general knowledge; verify them against provider documentation when building.

## What we can and cannot know about soil
- No cheap, accurate plot-level soil moisture from space for small plots.
- Rainfall: good for drought/flood context and index-style insurance.
- Vegetation water stress (e.g. NDMI) from Sentinel-2: plot scale, but it measures the plant, not the soil.
- Radar/SMAP soil moisture: coarse (hundreds of metres to many km), regional context only.
- Ground sensors: the only real plot-level moisture, probably too costly for smallholders now; optional later add-on.

## Python service contract (proposal)
- `POST /satellite/jobs` body: `{ plot_id, polygon_geojson, start_date }` -> `{ job_id, status }`
- `GET /satellite/plots/{plot_id}/timeseries` -> list of `{ date, ndvi_median, pixel_count, cloud_fraction, source }`
- `GET /satellite/plots/{plot_id}/rainfall` -> list of `{ date, rainfall_mm, source }`
- `GET /satellite/plots/{plot_id}/summary` -> `{ latest_image_date, flag, pixels_used, notes }`
- Keep the data-fetching layer behind an interface so sources can be swapped without changing the API.
- Python framework not confirmed (see README, open question 1).

## Environment note
The AI workspace that wrote this file cannot reach live satellite APIs. The first real fetch must be run and verified on the developer's machine.
