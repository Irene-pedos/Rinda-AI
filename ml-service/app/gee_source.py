"""Real data from Google Earth Engine: Sentinel-2 NDVI and CHIRPS rainfall."""

from datetime import date, timedelta

from app import params
from app.processing import Observation, RainDay

ORIGIN = "GEE"

S2_COLLECTION = "COPERNICUS/S2_SR_HARMONIZED"
CLOUD_COLLECTION = "COPERNICUS/S2_CLOUD_PROBABILITY"
CHIRPS_COLLECTION = "UCSB-CHG/CHIRPS/DAILY"


class GeeNotAuthorised(Exception):
    pass


def initialise(project_id: str):
    """Connects to Earth Engine, or raises GeeNotAuthorised with the reason."""
    if not project_id:
        raise GeeNotAuthorised("GEE_PROJECT_ID is empty in .env.")
    import ee

    try:
        ee.Initialize(project=project_id)
    except Exception as ex:
        raise GeeNotAuthorised(str(ex).splitlines()[0]) from ex
    return ee


def parse_s2(features: list[dict]) -> list[Observation]:
    result = []
    for feature in features:
        props = feature["properties"]
        clear = int(props.get("clear_pixels") or 0)
        total = int(props.get("total_pixels") or 0)
        result.append(Observation(
            day=date.fromisoformat(props["date"]),
            ndvi=props.get("ndvi"),
            clear_pixels=clear,
            cloud_pct=props.get("cloud_pct"),
            # Pixels inside the shrunk plot that the cloud and scene masks removed.
            cloud_fraction=round(1 - clear / total, 4) if total > 0 else None,
        ))
    return result


def parse_rain(features: list[dict]) -> list[RainDay]:
    return [
        RainDay(date.fromisoformat(f["properties"]["date"]), f["properties"].get("rain_mm"))
        for f in features
    ]


class GeeSource:
    origin = ORIGIN

    def __init__(self, project_id: str, boundary: dict):
        self.ee = initialise(project_id)
        self.geometry = self.ee.Geometry(boundary)
        self.region = self.geometry.buffer(-params.SHRINK_METRES, 1)

    def observations(self, start: date, end: date) -> list[Observation]:
        ee = self.ee
        window = (start.isoformat(), (end + timedelta(days=1)).isoformat())
        images = ee.ImageCollection(S2_COLLECTION).filterBounds(self.region).filterDate(*window)
        clouds = ee.ImageCollection(CLOUD_COLLECTION).filterBounds(self.region).filterDate(*window)
        joined = ee.ImageCollection(ee.Join.saveFirst("cloud").apply(
            primary=images,
            secondary=clouds,
            condition=ee.Filter.equals(leftField="system:index", rightField="system:index"),
        ))
        return parse_s2(joined.map(self._s2_feature).getInfo()["features"])

    def _s2_feature(self, image):
        ee = self.ee
        scl = image.select("SCL")
        scene_clear = scl.eq(params.SCL_CLEAR_CLASSES[0]).Or(scl.eq(params.SCL_CLEAR_CLASSES[1]))
        cloud_free = ee.Image(image.get("cloud")).select("probability").lt(params.CLOUD_PROBABILITY_MAX)
        index = image.normalizedDifference(["B8", "B4"]).rename("ndvi").updateMask(scene_clear.And(cloud_free))
        # Median, not mean (docs/02-satellite-monitoring.md), over pixels inside the plot shrunk by one pixel,
        # so pixels touching the boundary are left out.
        stats = index.reduceRegion(
            reducer=ee.Reducer.median().combine(ee.Reducer.count(), sharedInputs=True),
            geometry=self.region,
            scale=params.SENTINEL2_SCALE_METRES,
            maxPixels=1_000_000,
        )
        total = image.select("B4").reduceRegion(
            reducer=ee.Reducer.count(), geometry=self.region, scale=params.SENTINEL2_SCALE_METRES,
            maxPixels=1_000_000,
        )
        return ee.Feature(None, {
            "date": image.date().format("YYYY-MM-dd"),
            "ndvi": stats.get("ndvi_median"),
            "clear_pixels": stats.get("ndvi_count"),
            "total_pixels": total.get("B4"),
            "cloud_pct": image.get("CLOUDY_PIXEL_PERCENTAGE"),
        })

    def rain(self, start: date, end: date) -> list[RainDay]:
        ee = self.ee
        window = (start.isoformat(), (end + timedelta(days=1)).isoformat())
        # A CHIRPS pixel is about 5.5 km wide, so a plot sits inside one: read it at the plot's centre.
        centre = self.geometry.centroid(1)

        def to_feature(image):
            value = image.reduceRegion(
                reducer=ee.Reducer.first(), geometry=centre, scale=params.CHIRPS_SCALE_METRES
            ).get("precipitation")
            return ee.Feature(None, {"date": image.date().format("YYYY-MM-dd"), "rain_mm": value})

        collection = ee.ImageCollection(CHIRPS_COLLECTION).filterDate(*window)
        return parse_rain(collection.map(to_feature).getInfo()["features"])
