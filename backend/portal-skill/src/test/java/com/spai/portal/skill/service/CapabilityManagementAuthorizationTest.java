package com.spai.portal.skill.service;

import com.spai.portal.skill.controller.CapabilityManagementController;
import com.spai.portal.skill.controller.CapabilityController;
import java.util.Collections;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapabilityManagementAuthorizationTest {
    private AnnotationConfigApplicationContext context;
    private CapabilityManagementController controller;
    @Configuration @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class Config {
        @Bean CapabilityManagementService service() { return mock(CapabilityManagementService.class); }
        @Bean CapabilityController publicController(CapabilityManagementService service) { return new CapabilityController(service); }
        @Bean CapabilityManagementController controller(CapabilityManagementService service) { return new CapabilityManagementController(service); }
    }
    @BeforeEach void setup() { SecurityContextHolder.clearContext(); context = new AnnotationConfigApplicationContext(Config.class); controller = context.getBean(CapabilityManagementController.class); }
    @AfterEach void cleanup() { context.close(); SecurityContextHolder.clearContext(); }
    @Test void anonymousRequestsNeverReachGovernanceService() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.list(null, null, null));
        verifyNoInteractions(context.getBean(CapabilityManagementService.class));
    }
    @Test void ordinaryUserCannotReadHistoryOrTransitionOrDownload() {
        login("ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> controller.versions("asset"));
        assertThrows(AccessDeniedException.class, () -> controller.transition("asset", "version", "publish", Collections.emptyMap(), null));
        assertThrows(AccessDeniedException.class, () -> controller.download("asset", "version"));
        verifyNoInteractions(context.getBean(CapabilityManagementService.class));
    }
    @Test void authenticatedAdminCanReadGovernanceHistory() {
        login("ROLE_ADMIN"); assertDoesNotThrow(() -> controller.versions("asset"));
        verify(context.getBean(CapabilityManagementService.class)).history("asset");
    }
    @Test void adapterDetailsAndDownloadRequireAdmin() throws Exception {
        CapabilityController api = context.getBean(CapabilityController.class);
        login("ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> api.get("platform-skill-adapter"));
        assertThrows(AccessDeniedException.class, () -> api.download("platform-skill-adapter"));
        verifyNoInteractions(context.getBean(CapabilityManagementService.class));
        login("ROLE_ADMIN");
        assertDoesNotThrow(() -> api.get("platform-skill-adapter"));
        verify(context.getBean(CapabilityManagementService.class)).published("platform-skill-adapter");
    }
    private void login(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-account", "unused", Collections.singletonList(new SimpleGrantedAuthority(role)))); }
}
