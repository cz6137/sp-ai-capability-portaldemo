package com.spai.portal.integration.repository;

import com.spai.portal.integration.domain.SyncJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobRepository extends JpaRepository<SyncJob,String> {
    Optional<SyncJob> findFirstBySourceAndStatusOrderByStartedAtDesc(String source, String status);
}
