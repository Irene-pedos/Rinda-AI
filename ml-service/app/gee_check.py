"""Checks the Earth Engine setup. Run from ml-service: .venv\\Scripts\\python -m app.gee_check"""

import sys
from datetime import date

from app import gee_source
from app.config import get_settings

# A point in Rwanda used only to confirm the datasets respond. It is not a plot.
TEST_BOUNDARY = {
    "type": "Polygon",
    "coordinates": [[[30.100, -1.950], [30.102, -1.950], [30.102, -1.948], [30.100, -1.948], [30.100, -1.950]]],
}


def main() -> int:
    project = get_settings().gee_project_id
    print(f"Project: {project or '(empty)'}")
    try:
        source = gee_source.GeeSource(project, TEST_BOUNDARY)
    except gee_source.GeeNotAuthorised as ex:
        print(f"NOT AUTHORISED: {ex}")
        print("Run: .venv\\Scripts\\earthengine authenticate")
        return 1

    observations = source.observations(date(2025, 1, 1), date(2025, 1, 31))
    usable = [o for o in observations if o.usable]
    print(f"Sentinel-2, January 2025: {len(observations)} images, {len(usable)} usable")
    for obs in observations[:5]:
        print(f"  {obs.day} ndvi={obs.ndvi} clear_pixels={obs.clear_pixels} cloud_pct={obs.cloud_pct}")

    rain = source.rain(date(2025, 1, 1), date(2025, 1, 31))
    total = sum(day.rain_mm or 0 for day in rain)
    print(f"CHIRPS, January 2025: {len(rain)} days, {total:.1f} mm in total")
    print("Earth Engine is working.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
