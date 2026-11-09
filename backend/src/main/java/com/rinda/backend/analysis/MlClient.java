package com.rinda.backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

/** Calls the ML service. The ML service posts its results back to /internal/ml/results before it answers. */
public interface MlClient {

    /** One plot to analyse. For SAMPLE only plotId is used; GEE needs the real plot details. */
    record PlotJob(long plotId, String source, JsonNode boundary, LocalDate plantingDate, String seasonType,
                   LocalDate asOf) {
    }

    /** runId is the ml_runs row the result was saved as. */
    record Processed(long runId, String dataOrigin, String modelVersion) {
    }

    /** retryable is true for ML-service and network failures, false when a request was refused (4xx). */
    class MlCallException extends RuntimeException {
        private final int status;
        private final boolean retryable;

        public MlCallException(int status, String message, boolean retryable) {
            super(message);
            this.status = status;
            this.retryable = retryable;
        }

        public int status() {
            return status;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    Processed process(PlotJob job);

    /** The ML service's /health: model version and whether Earth Engine is authorised. */
    JsonNode health();
}
