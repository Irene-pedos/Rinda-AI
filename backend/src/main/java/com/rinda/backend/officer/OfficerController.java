package com.rinda.backend.officer;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.officer.OfficerDtos.CaseFile;
import com.rinda.backend.officer.OfficerDtos.QueueItem;
import com.rinda.backend.officer.OfficerDtos.Summary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The insurer dashboard (spec §1 users, §10.1). Decisions themselves are POST /claims/{id}/decision. */
@RestController
@RequestMapping("/api/v1/officer")
@Tag(name = "Officer dashboard")
@PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
public class OfficerController {

    private static final Set<String> STATUSES = Set.of("UNDER_REVIEW", "FIELD_VISIT_REQUESTED", "APPROVED", "DECLINED",
            "PAID", "REVIEW_REQUESTED", "FINAL");
    private static final Set<String> FLAGS = Set.of("LOW_CONFIDENCE_STATUS", "LOW_CONFIDENCE_FORECAST",
            "NORMAL_CURVE_ESTIMATED", "FIELD_VISIT_SUGGESTED", "SAMPLE_DATA");

    private final OfficerService service;

    public OfficerController(OfficerService service) {
        this.service = service;
    }

    @Operation(summary = "Claims queue, needs-attention first. status is a comma-separated list.")
    @GetMapping("/claims")
    public PageResponse<QueueItem> queue(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String flag,
                                         @RequestParam(required = false) Boolean demo,
                                         @RequestParam(required = false) Long cooperativeId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        List<String> statuses = status == null || status.isBlank() ? List.of()
                : Arrays.stream(status.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (!STATUSES.containsAll(statuses)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown claim status in 'status'.");
        }
        if (flag != null && !FLAGS.contains(flag)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown review flag.");
        }
        return service.queue(new OfficerService.QueueFilter(statuses, flag, demo, cooperativeId, q), page, size);
    }

    @Operation(summary = "One claim's case file: claim, farmer, plot, cover, evidence, decisions, reviews, history")
    @GetMapping("/claims/{id}")
    public CaseFile caseFile(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return service.caseFile(principal, id);
    }

    @Operation(summary = "Dashboard summary: claims by status, what is waiting, and missing trigger values")
    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }
}
