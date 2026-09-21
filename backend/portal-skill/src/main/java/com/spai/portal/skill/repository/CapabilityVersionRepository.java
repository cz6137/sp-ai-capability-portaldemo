package com.spai.portal.skill.repository;

import com.spai.portal.skill.domain.CapabilityVersion;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapabilityVersionRepository extends JpaRepository<CapabilityVersion, String> {
    Optional<CapabilityVersion> findByAssetIdAndVersionName(String assetId, String versionName);
    List<CapabilityVersion> findByAssetIdOrderByCreatedAtDesc(String assetId);
    List<CapabilityVersion> findByPackageFileId(String packageFileId);
}
