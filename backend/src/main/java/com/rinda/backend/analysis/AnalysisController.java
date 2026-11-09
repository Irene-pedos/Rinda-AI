package com.rinda.backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.analysis.AnalysisDtos.AnalysisRunRequest;
import com.rinda.backend.analysis.AnalysisDtos.AnalysisRunResponse;
import com.rinda.backend.analysis.MlClient.MlCallException;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin "Run analysis" button (spec §10.1) and its history. */
@RestController
@RequestMapping("/api/v1/admin/analysis-runs")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AnalysisController {

    private final AnalysisService service;
    private final MlClient ml;
    private final ObjectMapper mapper;

    public AnalysisController(AnalysisService service, MlClient ml, ObjectMapper mapper) {
        this.service = service;
        this.ml = ml;
        this.mapper = mapper;
    }

    @Operation(summary = "Run analysis now. Returns at once; follow progress with GET /admin/analysis-runs/{id}.")
    @PostMapping
    public ResponseEntity<AnalysisRunResponse> start(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @Valid @RequestBody(required = false) AnalysisRunRequest request) {
        AnalysisRunRequest r = request == null ? new AnalysisRunRequest(null, null, null) : request;
        long runId = service.start(principal.userId(), r.source() == null ? "GEE" : r.source(), r.plotIds(),
                r.asOf(), "MANUAL");
        service.launch(runId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.get(runId));
    }

    @Operation(summary = "Analysis runs, newest first")
    @GetMapping
    public PageResponse<AnalysisRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @Operation(summary = "One run with the result for each plot")
    @GetMapping("/{id}")
    public AnalysisRunResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "Is the ML service reachable, which model version, and is Earth Engine authorised?")
    @GetMapping("/ml-health")
    public JsonNode mlHealth() {
        try {
            ObjectNode health = ml.health().deepCopy();
            health.put("reachable", true);
            return health;
        } catch (MlCallException ex) {
            ObjectNode down = mapper.createObjectNode();
            down.put("reachable", false);
            down.put("detail", ex.getMessage());
            return down;
        }
    }
}
