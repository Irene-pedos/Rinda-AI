"""SIMULATED data for building and testing before Earth Engine is authorised. It is not satellite data."""

import json
from datetime import date
from pathlib import Path

from app.processing import Observation, RainDay

ORIGIN = "SAMPLE_FILE"
SAMPLE_FILE = Path(__file__).resolve().parents[1] / "samples" / "sample_plot.json"


class SampleSource:
    origin = ORIGIN

    def __init__(self, path: Path = SAMPLE_FILE):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("is_simulated") is not True:
            raise ValueError(f"{path} is not marked as simulated")
        self.planting_date = date.fromisoformat(data["planting_date"])
        self.season_type = data["season_type"]
        self.as_of = date.fromisoformat(data["as_of"])
        self._observations = [
            Observation(date.fromisoformat(o["date"]), o["ndvi"], o["clear_pixels"], o["cloud_pct"])
            for o in data["observations"]
        ]
        self._rain = [RainDay(date.fromisoformat(r["date"]), r["rain_mm"]) for r in data["rain"]]

    def observations(self, start: date, end: date) -> list[Observation]:
        return [o for o in self._observations if start <= o.day <= end]

    def rain(self, start: date, end: date) -> list[RainDay]:
        return [r for r in self._rain if start <= r.day <= end]
