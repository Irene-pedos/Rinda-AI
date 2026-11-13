# Rinda ML service

Python FastAPI service. It extracts Sentinel-2 NDVI and CHIRPS rainfall for a plot, builds a baseline from the plot's past seasons, and posts the results to the backend. The root README has the setup and run commands.

Only the backend calls this service, using the `X-Internal-Api-Key` header.

| Endpoint | Purpose |
|---|---|
| `GET /health` | Service status, and whether Earth Engine is authorised |
| `POST /process/plot` | Process one plot and post the results to the backend |
| `POST /monitor/plot` | Satellite monitoring: fetch raw images and rainfall for a window and run the maize season check. Saves nothing; returns the result to the backend, which stores it |

```json
{"plotId": 9001, "source": "sample"}
```

```json
{"plotId": 12, "source": "gee", "boundary": {"type": "Polygon", "coordinates": []}, "plantingDate": "2026-09-20", "seasonType": "A"}
```

`/monitor/plot` takes `plotId`, `source`, `boundary`, `plantingDate`, `fetchFrom`, `asOf`, `minClearPixels` (empty: no image counts) and `history` (observations the backend already stored, so a weekly refresh fetches only new images while the season check sees the whole season). Each image's NDVI is the **median** of its clear pixels inside the plot shrunk by one pixel. The season check (`CONSISTENT`, `INCONSISTENT`, `NOT_ENOUGH_DATA`) is a review flag, never proof of crop; its settings are `SEASON_*` in `app/params.py`, all TO_CONFIRM.

## Real data and sample data

- `source: "gee"` uses Earth Engine. If it isn't authorised, the request fails with 503. It never falls back to the sample.
- `source: "sample"` uses `samples/sample_plot.json`, which is **simulated**: it is not satellite data and not a real plot. `samples/generate_sample.py` rebuilds it exactly.

Every saved row carries `data_origin` (`GEE` or `SAMPLE_FILE`). The backend stores sample data on demo plots only, and real data only on verified plots with confirmed details.

## Code layout

- `app/processing.py`: the calculations, with no Earth Engine dependency.
- `app/pipeline.py`: turns a data source into the result sent to the backend.
- `app/gee_source.py`, `app/sample_source.py`: the two data sources, behind the `DataSource` interface in `app/pipeline.py`. Another provider (Planetary Computer, Copernicus Data Space) only needs `observations()` and `rain()`.
- `app/params.py`: every processing setting. Each run stores them. Change `MODEL_VERSION` when a value changes.

## Limits of the baseline

- It assumes the plot was planted on the same calendar day in past years.
- It uses the plot's own history, and that land may not have grown maize every year.
- Cloud cover leaves gaps. In a test run over one Rwandan location, 11 of 27 dates were usable, and 13 of 16 baseline bins had enough values. Missing bins are left out, never filled in.

## Compiled packages on the lab computer

Windows Application Control on the lab computer blocked a pandas DLL (`offsets.pyd`), so this service doesn't use pandas. If a new package fails with "An Application Control policy has blocked this file", don't try to bypass the policy; find another way or ask whoever manages the computer.
