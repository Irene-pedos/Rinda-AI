"""Rinda ML service. Only the backend calls it, using the internal API key."""

import hmac
from datetime import date, datetime
from typing import Annotated, Literal
from zoneinfo import ZoneInfo

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, model_validator

from app import backend_client, gee_source, params, pipeline
from app.config import Settings, get_settings
from app.sample_source import SampleSource

app = FastAPI(title="Rinda ML service", version=params.MODEL_VERSION)

SettingsDep = Annotated[Settings, Depends(get_settings)]


def require_key(settings: SettingsDep, x_internal_api_key: Annotated[str | None, Header()] = None) -> None:
    if x_internal_api_key is None or not hmac.compare_digest(
        x_internal_api_key.encode(), settings.internal_api_key.encode()
    ):
        raise HTTPException(status_code=401, detail="A valid internal API key is required.")


class ProcessPlotRequest(BaseModel):
    plotId: int
    source: Literal["gee", "sample"]
    boundary: dict | None = None
    plantingDate: date | None = None
    seasonType: Literal["A", "B"] | None = None
    asOf: date | None = Field(default=None, description="Last day to include. Defaults to today in Rwanda.")

    @model_validator(mode="after")
    def gee_needs_plot_details(self):
        if self.source == "gee" and not (self.boundary and self.plantingDate and self.seasonType):
            raise ValueError("source 'gee' needs boundary, plantingDate and seasonType")
        return self


def _sample_job(request: ProcessPlotRequest, source: SampleSource) -> pipeline.PlotJob:
    return pipeline.PlotJob(request.plotId, source.planting_date, source.season_type, source.as_of)


def _gee_job(request: ProcessPlotRequest) -> pipeline.PlotJob:
    as_of = request.asOf or datetime.now(ZoneInfo("Africa/Kigali")).date()
    return pipeline.PlotJob(request.plotId, request.plantingDate, request.seasonType, as_of)


def _open_gee(settings: Settings, boundary: dict) -> gee_source.GeeSource:
    # Never falls back to the sample file: a caller asking for real data must get real data or an error.
    try:
        return gee_source.GeeSource(settings.gee_project_id, boundary)
    except gee_source.GeeNotAuthorised as ex:
        raise HTTPException(status_code=503, detail=f"Earth Engine is not available: {ex}") from ex


@app.get("/health")
def health(settings: SettingsDep) -> dict:
    gee = {"projectId": settings.gee_project_id, "authorised": True, "detail": None}
    try:
        gee_source.initialise(settings.gee_project_id)
    except gee_source.GeeNotAuthorised as ex:
        gee.update(authorised=False, detail=str(ex))
    return {"status": "ok", "modelVersion": params.MODEL_VERSION, "gee": gee}


@app.post("/process/plot", dependencies=[Depends(require_key)])
def process_plot(request: ProcessPlotRequest, settings: SettingsDep) -> dict:
    if request.source == "sample":
        source = SampleSource()
        job = _sample_job(request, source)
    else:
        source = _open_gee(settings, request.boundary)
        job = _gee_job(request)

    try:
        result = pipeline.run(job, source)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    try:
        saved = backend_client.post_results(settings, result)
    except backend_client.BackendRejected as ex:
        status = ex.status if 400 <= ex.status < 500 else 502
        raise HTTPException(status_code=status, detail=ex.body) from ex

    return {
        "plotId": request.plotId,
        "dataOrigin": result["dataOrigin"],
        "modelVersion": result["modelVersion"],
        "saved": saved,
    }
