package com.spai.portal.integration.controller;

import com.spai.portal.common.ApiResponse;
import com.spai.portal.integration.dto.MeetingMinutesDtos.Capability;
import com.spai.portal.integration.dto.MeetingMinutesDtos.JobView;
import com.spai.portal.integration.service.MeetingMinutesService;
import com.spai.portal.security.PortalPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/tools/meeting-minutes")
public class MeetingMinutesController {
    private final MeetingMinutesService service;

    public MeetingMinutesController(MeetingMinutesService service) { this.service = service; }

    @GetMapping("/capabilities")
    public ApiResponse<Capability> capabilities() { return ApiResponse.ok(service.capabilities()); }

    @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JobView> start(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "subject", required = false) String subject,
        @RequestParam(value = "attendees", required = false) String attendees,
        @RequestParam(value = "background", required = false) String background,
        @RequestParam(value = "template", defaultValue = "通用会议") String template,
        @RequestParam(value = "mode", defaultValue = "minutes") String mode,
        @RequestParam(value = "durationSeconds", defaultValue = "1") long durationSeconds,
        @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.start(file, subject, attendees, background, template, mode, durationSeconds, principal.getUserId()));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<JobView> job(@PathVariable String id, @AuthenticationPrincipal PortalPrincipal principal) {
        return ApiResponse.ok(service.get(id, principal.getUserId()));
    }
}
