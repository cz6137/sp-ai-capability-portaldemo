package com.spai.portal.skill.service;

import com.spai.portal.common.BusinessException;
import com.spai.portal.skill.domain.CapabilityAsset;
import com.spai.portal.skill.domain.CapabilityVersion;
import com.spai.portal.skill.repository.CapabilityAssetRepository;
import com.spai.portal.skill.repository.CapabilityVersionRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapabilityFileReadPolicyTest {
    private CapabilityAssetRepository assets;
    private CapabilityVersionRepository versions;
    private CapabilityFileReadPolicy policy;
    private CapabilityAsset asset;
    private CapabilityVersion version;
    @BeforeEach void setup() {
        assets = mock(CapabilityAssetRepository.class); versions = mock(CapabilityVersionRepository.class); policy = new CapabilityFileReadPolicy(assets, versions);
        asset = new CapabilityAsset(); asset.setId("asset"); asset.setStatus("PUBLISHED"); asset.setCurrentVersionId("version");
        version = new CapabilityVersion(); version.setId("version"); version.setAssetId("asset"); version.setStatus("PUBLISHED");
        when(versions.findByPackageFileId("package")).thenReturn(Arrays.asList(version)); when(assets.findById("asset")).thenReturn(Optional.of(asset));
        SecurityContextHolder.clearContext();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void publishedCurrentPackageCanUseGenericRoute() { assertDoesNotThrow(() -> policy.checkRead("package")); }
    @Test void draftWithdrawnAndHistoricalPackagesCannotBypassGovernance() {
        asset.setStatus("ARCHIVED"); assertThrows(BusinessException.class, () -> policy.checkRead("package"));
        asset.setStatus("PUBLISHED"); asset.setCurrentVersionId("new-version"); assertThrows(BusinessException.class, () -> policy.checkRead("package"));
        asset.setCurrentVersionId("version"); version.setStatus("DRAFT"); assertThrows(BusinessException.class, () -> policy.checkRead("package"));
    }
    @Test void onlyAuthenticatedAdminCanReadUnpublishedPackages() {
        asset.setStatus("DRAFT");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "unused", Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThrows(BusinessException.class, () -> policy.checkRead("package"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "unused", Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertDoesNotThrow(() -> policy.checkRead("package"));
    }
    @Test void filesOutsideCapabilityDomainRetainExistingPolicy() {
        when(versions.findByPackageFileId("other-file")).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> policy.checkRead("other-file"));
    }
}
