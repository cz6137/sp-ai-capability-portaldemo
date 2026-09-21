package com.spai.portal.integration.controller;

import com.spai.portal.common.ApiResponse;
import com.spai.portal.integration.domain.SyncJob;
import com.spai.portal.integration.dto.ImaBridgeDtos.*;
import com.spai.portal.integration.service.ImaBridgeService;
import com.spai.portal.security.PortalPrincipal;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/bridge/ima/runs")
@PreAuthorize("hasRole('ADMIN')")
public class ImaBridgeController {
    private final ImaBridgeService service;
    public ImaBridgeController(ImaBridgeService service) { this.service = service; }

    @PostMapping
    public ApiResponse<SyncJob> start(@Valid @RequestBody StartRequest request,
        @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.start(request, principal.getUserId()));
    }

    @PostMapping("/{runId}/assets")
    public ApiResponse<BatchResult> assets(@PathVariable String runId, @Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(service.upsert(runId, request));
    }

    @PostMapping("/{runId}/complete")
    public ApiResponse<SyncJob> complete(@PathVariable String runId) { return ApiResponse.ok(service.complete(runId)); }

    @PostMapping("/{runId}/fail")
    public ApiResponse<SyncJob> fail(@PathVariable String runId, @RequestBody(required = false) FailRequest request) {
        return ApiResponse.ok(service.fail(runId, request));
    }
}
