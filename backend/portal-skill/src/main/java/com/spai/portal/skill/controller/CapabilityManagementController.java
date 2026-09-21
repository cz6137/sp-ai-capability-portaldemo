package com.spai.portal.skill.controller;

import com.spai.portal.common.ApiResponse;
import com.spai.portal.audit.domain.AuditLog;
import com.spai.portal.asset.service.FileStorageService;
import java.io.IOException;
import java.net.URLEncoder;
import com.spai.portal.security.PortalPrincipal;
import com.spai.portal.skill.service.CapabilityManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/capabilities")
@PreAuthorize("hasRole('ADMIN')")
public class CapabilityManagementController {
    private final CapabilityManagementService service;
    public CapabilityManagementController(CapabilityManagementService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
        @RequestParam(required = false) String kind, @RequestParam(required = false) String submitter) {
        return ApiResponse.ok(service.list(status, kind, submitter));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importCapability(@RequestPart("manifest") String manifest,
        @RequestPart(value = "package", required = false) MultipartFile packageFile,
        @RequestParam(required = false) String expectedUpdatedAt,
        @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.importManifest(manifest, packageFile, principal.getUserId(), expectedUpdatedAt));
    }

    @GetMapping("/{assetId}/versions")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String assetId) {
        return ApiResponse.ok(service.history(assetId));
    }

    @GetMapping("/{assetId}/versions/{versionId}/audits")
    public ApiResponse<List<AuditLog>> audits(@PathVariable String assetId, @PathVariable String versionId) {
        return ApiResponse.ok(service.auditHistory(assetId, versionId));
    }

    @PostMapping("/{assetId}/versions/{versionId}/actions/{action}")
    public ApiResponse<Map<String, Object>> transition(@PathVariable String assetId, @PathVariable String versionId,
        @PathVariable String action, @RequestBody Map<String, String> body,
        @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.transition(assetId, versionId, action, body.get("reason"), body.get("expectedUpdatedAt"), principal.getUserId()));
    }

    @GetMapping("/{assetId}/versions/{versionId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String assetId, @PathVariable String versionId) throws IOException {
        FileStorageService.StoredFile file = service.adminDownload(assetId, versionId);
        String name = URLEncoder.encode(file.metadata.getFileName(), "UTF-8").replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.metadata.getFileSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name).cacheControl(CacheControl.noStore()).body(file.resource);
    }
}
