package com.rinda.backend.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Asks the ML service for one plot's satellite images and rainfall (POST /monitor/plot). The ML service saves
 * nothing; the backend stores what comes back. Failures throw {@link com.rinda.backend.analysis.MlClient.MlCallException}.
 */
public interface SatelliteClient {

    /** fetchFrom, asOf and plantingDate are null for SAMPLE: the simulated file sets its own dates. */
    record MonitorRequest(long plotId, String source, JsonNode boundary, LocalDate plantingDate, LocalDate fetchFrom,
                          LocalDate asOf, Integer minClearPixels, List<HistoryRow> history) {
    }

    /** An observation already stored for this boundary, so the season check sees the whole season. */
    record HistoryRow(LocalDate date, BigDecimal ndviMedian, int pixelCount, BigDecimal cloudFraction) {
    }

    JsonNode monitor(MonitorRequest request);
}
