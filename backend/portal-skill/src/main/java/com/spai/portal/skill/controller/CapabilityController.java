package com.spai.portal.skill.controller;

import com.spai.portal.common.ApiResponse;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.skill.service.CapabilityManagementService;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilityController {
    private final CapabilityManagementService service;
    public CapabilityController(CapabilityManagementService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String kind) {
        return ApiResponse.ok(service.publishedList(kind));
    }

    @PreAuthorize("#p0 != 'platform-skill-adapter' or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @GetMapping("/{slug}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String slug) { return ApiResponse.ok(service.published(slug)); }

    @PreAuthorize("#p0 != 'platform-skill-adapter' or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @GetMapping("/{slug}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String slug) throws IOException {
        FileStorageService.StoredFile file = service.download(slug);
        String encoded = URLEncoder.encode(file.metadata.getFileName(), "UTF-8").replace("+", "%20");
        String contentType = file.metadata.getContentType() == null ? "application/octet-stream" : file.metadata.getContentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).contentLength(file.metadata.getFileSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded).cacheControl(CacheControl.noStore()).body(file.resource);
    }
}
