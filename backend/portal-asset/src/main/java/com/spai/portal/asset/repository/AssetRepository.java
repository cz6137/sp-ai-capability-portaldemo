package com.spai.portal.asset.repository;

import com.spai.portal.asset.domain.Asset;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset,String> {
    @Query("select a from Asset a where a.enabled=true and a.status='PUBLISHED' and (:stage is null or a.stageId=:stage) and (:type is null or a.type=:type) and (:q is null or lower(a.searchText) like lower(concat('%',:q,'%'))) order by a.stageId,a.name")
    List<Asset> search(@Param("stage") Integer stage, @Param("type") String type, @Param("q") String q);

    @Query("select a from Asset a where a.enabled=true and a.status='PUBLISHED' and a.type='C' and a.caseCategoryId=:categoryId order by a.stageId,a.name")
    List<Asset> findPublishedCases(@Param("categoryId") String categoryId);

    Optional<Asset> findByMediaId(String mediaId);
    List<Asset> findByMediaIdIsNotNull();
    List<Asset> findBySourceType(String sourceType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Asset a set a.enabled=false where a.sourceType='IMA_BRIDGE' and (a.lastSyncRunId is null or a.lastSyncRunId<>:runId)")
    int disableMissingImaBridgeAssets(@Param("runId") String runId);
}
