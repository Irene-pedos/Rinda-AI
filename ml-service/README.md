# Rinda ML service

Python FastAPI service. It extracts Sentinel-2 NDVI and CHIRPS rainfall for a plot, builds a baseline from the plot's past seasons, and posts the results to the backend. The root README has the setup and run commands.

Only the backend calls this service, using the `X-Internal-Api-Key` header.

| Endpoint | Purpose |
|---|---|
| `GET /health` | Service status, and whether Earth Engine is authorised |
| `POST /process/plot` | Process one plot and post the results to the backend |

```json
{"plotId": 9001, "source": "sample"}
```

```json
{"plotId": 12, "source": "gee", "boundary": {"type": "Polygon", "coordinates": []}, "plantingDate": "2026-09-20", "seasonType": "A"}
```

## Real data and sample data

- `source: "gee"` uses Earth Engine. If it isn't authorised, the request fails with 503. It never falls back to the sample.
- `source: "sample"` uses `samples/sample_plot.json`, which is **simulated**: it is not satellite data and not a real plot. `samples/generate_sample.py` rebuilds it exactly.

Every saved row carries `data_origin` (`GEE` or `SAMPLE_FILE`). The backend stores sample data on demo plots only, and real data only on verified plots with confirmed details.

## Code layout

- `app/processing.py`: the calculations, with no Earth Engine dependency.
- `app/pipeline.py`: turns a data source into the result sent to the backend.
- `app/gee_source.py`, `app/sample_source.py`: the two data sources.
- `app/params.py`: every processing setting. Each run stores them. Change `MODEL_VERSION` when a value changes.

## Limits of the baseline

- It assumes the plot was planted on the same calendar day in past years.
- It uses the plot's own history, and that land may not have grown maize every year.

## Compiled packages on the lab computer

Windows Application Control on the lab computer blocked a pandas DLL (`offsets.pyd`), so this service doesn't use pandas. If a new package fails with "An Application Control policy has blocked this file", don't try to bypass the policy; find another way or ask whoever manages the computer.
