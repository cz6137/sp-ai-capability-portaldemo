package com.spai.portal.skill.repository;

import com.spai.portal.skill.domain.CapabilityAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;

public interface CapabilityAssetRepository extends JpaRepository<CapabilityAsset, String> {
    Optional<CapabilityAsset> findBySlug(String slug);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CapabilityAsset a where a.slug = :slug")
    Optional<CapabilityAsset> lockBySlug(@Param("slug") String slug);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CapabilityAsset a where a.id = :id")
    Optional<CapabilityAsset> lockById(@Param("id") String id);
    List<CapabilityAsset> findAllByOrderByUpdatedAtDesc();
    List<CapabilityAsset> findByStatusOrderByUpdatedAtDesc(String status);
    List<CapabilityAsset> findByCreatedByOrderByUpdatedAtDesc(String createdBy);
}
