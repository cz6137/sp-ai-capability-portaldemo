package com.spai.portal.skill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.common.ApiResponse;
import com.spai.portal.security.PortalPrincipal;
import com.spai.portal.skill.domain.Skill;
import com.spai.portal.skill.domain.SkillVersion;
import com.spai.portal.skill.service.SkillService;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {
    private final SkillService service;
    private final ObjectMapper json;

    public SkillController(SkillService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping
    public ApiResponse<List<Skill>> list(@RequestParam(required = false) Integer stage, @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.published(stage, q));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String id) throws IOException {
        FileStorageService.StoredFile file = service.download(id);
        String encoded = URLEncoder.encode(file.metadata.getFileName(), "UTF-8").replace("+", "%20");
        String contentType = file.metadata.getContentType() == null ? "application/octet-stream" : file.metadata.getContentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).contentLength(file.metadata.getFileSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
            .cacheControl(CacheControl.noCache()).body(file.resource);
    }

    @GetMapping("/manage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> managed() { return ApiResponse.ok(service.managed()); }

    @PostMapping("/manage")
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD','MEMBER')")
    public ApiResponse<Skill> create(@RequestBody SkillService.SkillForm form, @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.save(null, form, principal.getUserId()));
    }

    @PutMapping("/manage/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD','MEMBER')")
    public ApiResponse<Skill> update(@PathVariable String id, @RequestBody SkillService.SkillForm form, @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.save(id, form, principal.getUserId()));
    }

    @PostMapping(value = "/manage/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD','MEMBER')")
    public ApiResponse<Skill> importPackage(@RequestPart("file") MultipartFile file, @RequestPart("metadata") String metadata,
        @AuthenticationPrincipal PortalPrincipal principal) throws IOException {
        SkillService.SkillForm form = json.readValue(metadata, SkillService.SkillForm.class);
        boolean publish = principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ApiResponse.ok(service.importPackage(file, form, principal.getUserId(), publish));
    }

    @DeleteMapping("/manage/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD')")
    public ApiResponse<Void> archive(@PathVariable String id) { service.archive(id); return ApiResponse.ok(null); }

    @PostMapping("/versions/{id}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD') or #action=='submit'")
    public ApiResponse<SkillVersion> action(@PathVariable String id, @PathVariable String action,
        @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(service.transition(id, action, body == null ? null : body.get("note")));
    }
}
