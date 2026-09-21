package com.spai.portal.asset.controller;

import com.spai.portal.asset.domain.AssetVersion;
import com.spai.portal.asset.service.AssetService;
import com.spai.portal.common.ApiResponse;
import com.spai.portal.security.PortalPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AssetController {
    private final AssetService service;
    public AssetController(AssetService service) { this.service = service; }

    @GetMapping("/stages")
    public ApiResponse<List<Map<String, Object>>> stages() { return ApiResponse.ok(service.stages()); }

    @GetMapping("/assets")
    public ApiResponse<List<Map<String, Object>>> assets(@RequestParam(required = false) Integer stage,
        @RequestParam(required = false) String type, @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.search(stage, type, q));
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }

    @GetMapping("/case-categories")
    public ApiResponse<List<Map<String, Object>>> caseCategories() { return ApiResponse.ok(service.caseCategories()); }

    @GetMapping("/case-categories/{code}")
    public ApiResponse<Map<String, Object>> cases(@PathVariable String code) { return ApiResponse.ok(service.cases(code)); }

    @PostMapping("/assets/versions/{id}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD') or #action=='submit'")
    public ApiResponse<AssetVersion> action(@PathVariable String id, @PathVariable String action,
        @RequestBody(required = false) Map<String, String> body, @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.transition(id, action, body == null ? null : body.get("note"), principal.getUserId()));
    }
}
