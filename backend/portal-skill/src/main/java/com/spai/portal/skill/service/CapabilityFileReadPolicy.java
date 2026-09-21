package com.spai.portal.skill.service;

import com.spai.portal.asset.service.FileReadPolicy;
import com.spai.portal.common.BusinessException;
import com.spai.portal.skill.domain.CapabilityAsset;
import com.spai.portal.skill.domain.CapabilityVersion;
import com.spai.portal.skill.repository.CapabilityAssetRepository;
import com.spai.portal.skill.repository.CapabilityVersionRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CapabilityFileReadPolicy implements FileReadPolicy {
    private final CapabilityAssetRepository assets;
    private final CapabilityVersionRepository versions;
    public CapabilityFileReadPolicy(CapabilityAssetRepository assets, CapabilityVersionRepository versions) { this.assets = assets; this.versions = versions; }

    @Override @Transactional(readOnly = true)
    public void checkRead(String fileId) {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        if (actor != null && actor.isAuthenticated() && actor.getAuthorities().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()))) return;
        List<CapabilityVersion> references = versions.findByPackageFileId(fileId);
        if (references.isEmpty()) return; // Other asset domains retain their own policy.
        for (CapabilityVersion version : references) {
            CapabilityAsset asset = assets.findById(version.getAssetId()).orElse(null);
            if (asset != null && "PUBLISHED".equals(asset.getStatus()) && "PUBLISHED".equals(version.getStatus()) && version.getId().equals(asset.getCurrentVersionId())) return;
        }
        throw BusinessException.notFound("能力交付包尚未发布或已下架");
    }
}
