package com.spai.portal.integration.repository;

import com.spai.portal.integration.domain.ImaBridgeRunItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImaBridgeRunItemRepository extends JpaRepository<ImaBridgeRunItem, String> {
    Optional<ImaBridgeRunItem> findByRunIdAndMediaId(String runId, String mediaId);
    long countByRunId(String runId);
}
