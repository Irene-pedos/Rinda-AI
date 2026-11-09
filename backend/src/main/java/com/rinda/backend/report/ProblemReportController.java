package com.rinda.backend.report;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import com.rinda.backend.report.ProblemReportDtos.ProblemReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/problem-reports")
@Tag(name = "Problem reports")
public class ProblemReportController {

    private final ProblemReportService service;

    public ProblemReportController(ProblemReportService service) {
        this.service = service;
    }

    @Operation(summary = "Farmer: report a problem on your plot. Evidence only: it never starts a claim.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ProblemReportResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam long plotId,
            @RequestParam String reason,
            @RequestParam(required = false) String note,
            @RequestParam(defaultValue = "false") boolean agentVisitRequested,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Instant reportedAt,
            @RequestPart(required = false) MultipartFile photo) {
        var created = service.create(principal.userId(), plotId, reason, note, agentVisitRequested,
                blankToNull(clientId), reportedAt, photo);
        return ResponseEntity.status(created.isNew() ? HttpStatus.CREATED : HttpStatus.OK).body(created.report());
    }

    @Operation(summary = "Problem reports you can see, newest first")
    @GetMapping
    public PageResponse<ProblemReportResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestParam(required = false) Long plotId,
                                                    @RequestParam(required = false) Long claimId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(principal, plotId, claimId, page, size);
    }

    @Operation(summary = "The report's photo, for people who can see the report")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        StoredPhoto photo = service.photo(principal, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.bytes());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
