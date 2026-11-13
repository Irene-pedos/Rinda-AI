package com.rinda.app.data.model;

/**
 * GET /plots/{id}/monitoring, the fields the plot screen needs (docs/API.md, docs/02-satellite-monitoring.md).
 * {@code state} drives the crop health card: NO_BOUNDARY, FETCHING, FETCH_FAILED, NO_CLEAR_IMAGE, DATA_AVAILABLE.
 * Satellite data is never live: always show {@code latestImageDate}.
 */
public class PlotMonitoringResponse {
    public Long plotId;
    public String state;
    public String message;
    public String verificationLevel;
    public String verificationNote;
    public boolean healthStatusAvailable;
    public boolean isDemo;
    public String dataOrigin;
    public String latestImageDate;
    public String monitoredSince;
    public boolean refreshing;
}
