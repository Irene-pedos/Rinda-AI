package com.rinda.backend.plot;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import com.rinda.backend.monitoring.MonitoringDtos.MonitoringResponse;
import com.rinda.backend.monitoring.MonitoringQueries;
import com.rinda.backend.plot.PlotDtos.DocumentResponse;
import com.rinda.backend.plot.PlotDtos.FlagResolveRequest;
import com.rinda.backend.plot.PlotDtos.PlotCreateRequest;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import com.rinda.backend.plot.PlotDtos.PlotUpdateRequest;
import com.rinda.backend.plot.PlotDtos.WalkRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/plots")
@Tag(name = "Plots")
public class PlotController {

    private final PlotService service;
    private final WalkService walks;
    private final PlotDocumentService documents;
    private final MonitoringQueries monitoring;

    public PlotController(PlotService service, WalkService walks, PlotDocumentService documents,
                          MonitoringQueries monitoring) {
        this.monitoring = monitoring;
        this.service = service;
        this.walks = walks;
        this.documents = documents;
    }

    @Operation(summary = "List plots: farmers see their own, agents their cooperative's, officers and admins all")
    @GetMapping
    public PageResponse<PlotResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(principal, page, size);
    }

    @Operation(summary = "Get one plot with its level, flags and checks. Returns 404 if not visible to this user.")
    @GetMapping("/{id}")
    public PlotResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return service.get(principal, id);
    }

    @Operation(summary = "Satellite monitoring: NDVI curve, rainfall, latest image date and the maize season "
            + "flag, or the empty state. Never live; a review flag, never proof of crop.")
    @GetMapping("/{id}/monitoring")
    public MonitoringResponse monitoring(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return monitoring.view(service.findVisible(principal, id));
    }

    @Operation(summary = "Start a maize plot registration: UPI, planting date, relationship, declaration. "
            + "A boundary is refused (400 BOUNDARY_NOT_ACCEPTED): it comes only from the GPS walk.")
    @PostMapping
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlotResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody PlotCreateRequest request) {
        return service.create(principal, request);
    }

    @Operation(summary = "Rename your plot or correct its planting date (locked after the field check)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    public PlotResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                               @Valid @RequestBody PlotUpdateRequest request) {
        return service.update(principal, id, request);
    }

    @Operation(summary = "Delete your plot. Returns 409 PLOT_DELETE_DEFERRED if it has active cover or an open claim.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        service.delete(principal, id);
    }

    @Operation(summary = "Submit the GPS boundary walk. The server builds and checks the polygon from the points.")
    @PostMapping("/{id}/walk")
    @PreAuthorize("hasRole('FARMER')")
    public PlotResponse walk(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                             @Valid @RequestBody WalkRequest request) {
        return walks.submit(principal, id, request);
    }

    @Operation(summary = "Upload your e-title, a portal screenshot, a lease or the owner's consent (JPEG, PNG or PDF)")
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                           @RequestParam DocumentType type,
                                           @RequestParam(required = false) BigDecimal documentAreaM2,
                                           @RequestPart(required = false) MultipartFile file) {
        return documents.upload(principal, id, type, documentAreaM2, file);
    }

    @Operation(summary = "The plot's documents (details only), with the same visibility as the plot")
    @GetMapping("/{id}/documents")
    public List<DocumentResponse> listDocuments(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable long id) {
        return documents.list(principal, id);
    }

    @Operation(summary = "One document's file, with the same visibility as the plot")
    @GetMapping("/{id}/documents/{documentId}/file")
    public ResponseEntity<byte[]> documentFile(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long id, @PathVariable long documentId) {
        StoredPhoto file = documents.file(principal, id, documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore()).body(file.bytes());
    }

    @Operation(summary = "Resolve a review flag after checking it (agent of the cooperative, officer or admin)")
    @PostMapping("/{id}/flags/{flagId}/resolve")
    @PreAuthorize("hasAnyRole('AGENT', 'OFFICER', 'ADMIN')")
    public PlotResponse resolveFlag(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                    @PathVariable long flagId, @Valid @RequestBody FlagResolveRequest request) {
        return service.resolveFlag(principal, id, flagId, request.note());
    }
}
