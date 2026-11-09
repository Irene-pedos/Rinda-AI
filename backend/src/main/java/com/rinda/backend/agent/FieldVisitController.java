package com.rinda.backend.agent;

import com.rinda.backend.agent.AgentDtos.FieldVisitResponse;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Field visits as evidence, for agents (their cooperative), officers and admins. */
@RestController
@RequestMapping("/api/v1/field-visits")
@Tag(name = "Agent")
@PreAuthorize("hasAnyRole('AGENT', 'OFFICER', 'ADMIN')")
public class FieldVisitController {

    private final AgentService service;

    public FieldVisitController(AgentService service) {
        this.service = service;
    }

    @Operation(summary = "Field visits you can see, newest first")
    @GetMapping
    public PageResponse<FieldVisitResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @RequestParam(required = false) Long plotId,
                                                 @RequestParam(required = false) Long claimId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.visits(principal, plotId, claimId, page, size);
    }

    @Operation(summary = "The visit's photo")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        StoredPhoto photo = service.visitPhoto(principal, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.bytes());
    }
}
