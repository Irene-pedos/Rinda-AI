"""Writes sample_plot.json: SIMULATED data with a fixed random seed, so the file can be rebuilt exactly.

Run from ml-service: .venv\\Scripts\\python samples\\generate_sample.py
The curve shapes are invented for testing. They say nothing about real maize in Rwanda.
"""

import json
from datetime import date, timedelta
from pathlib import Path

import numpy as np

PLANTING = date(2025, 9, 20)
AS_OF = date(2025, 12, 10)
PAST_YEARS = range(2019, 2025)
REVISIT_DAYS = 5
SEASON_DAYS = 150
STRESS_FROM_DAP = 55

rng = np.random.default_rng(2025)


def curve(dap: int) -> float:
    return 0.22 + 0.5 * np.exp(-(((dap - 75) / 38) ** 2))


def observation(day: date, value: float) -> dict:
    cloudy = rng.random() < 0.25
    return {
        "date": day.isoformat(),
        "ndvi": None if cloudy else round(float(value), 4),
        "clear_pixels": int(rng.integers(0, 6)) if cloudy else int(rng.integers(180, 240)),
        "cloud_pct": round(float(rng.uniform(70, 100) if cloudy else rng.uniform(0, 30)), 2),
    }


def season(planting: date, days: int, stressed: bool) -> list[dict]:
    rows = []
    for dap in range(0, days + 1, REVISIT_DAYS):
        value = curve(dap) + rng.normal(0, 0.02)
        if stressed and dap >= STRESS_FROM_DAP:
            value -= 0.006 * (dap - STRESS_FROM_DAP)
        rows.append(observation(planting + timedelta(days=dap), value))
    return rows


observations = []
for year in PAST_YEARS:
    observations += season(PLANTING.replace(year=year), SEASON_DAYS, stressed=False)
observations += season(PLANTING, (AS_OF - PLANTING).days, stressed=True)

rain = [
    {
        "date": (PLANTING + timedelta(days=i)).isoformat(),
        "rain_mm": round(float(rng.gamma(0.6, 6.0)), 2) if rng.random() < 0.5 else 0.0,
    }
    for i in range((AS_OF - PLANTING).days + 1)
]

sample = {
    "notice": "SIMULATED DATA. Not satellite data and not a real plot. For development and demo plots only.",
    "is_simulated": True,
    "season_type": "A",
    "planting_date": PLANTING.isoformat(),
    "as_of": AS_OF.isoformat(),
    "observations": observations,
    "rain": rain,
}
path = Path(__file__).with_name("sample_plot.json")
path.write_text(json.dumps(sample, indent=1) + "\n", encoding="utf-8")
print(f"Wrote {path}")
