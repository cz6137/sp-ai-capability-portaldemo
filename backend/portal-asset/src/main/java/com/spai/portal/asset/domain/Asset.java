package com.spai.portal.asset.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "asset")
public class Asset {
    @Id private String id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 1) private String type;
    @Column(name = "stage_id", nullable = false) private Integer stageId;
    @Column(name = "category_id", nullable = false) private String categoryId;
    @Column(name = "case_category_id") private String caseCategoryId;
    @Column(name = "media_id") private String mediaId;
    @Column(name = "source_url", length = 2048) private String sourceUrl;
    @Column(name = "source_type", nullable = false, length = 30) private String sourceType = "INTERNAL";
    @Column(name = "source_metadata", columnDefinition = "text") private String sourceMetadata;
    @Column(name = "last_sync_run_id") private String lastSyncRunId;
    @Column(name = "last_synced_at") private OffsetDateTime lastSyncedAt;
    @Column(name = "current_file_id") private String currentFileId;
    @Column(nullable = false) private String status = "PUBLISHED";
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "search_text", columnDefinition = "text") private String searchText;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getStageId() { return stageId; }
    public void setStageId(Integer stageId) { this.stageId = stageId; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCaseCategoryId() { return caseCategoryId; }
    public void setCaseCategoryId(String caseCategoryId) { this.caseCategoryId = caseCategoryId; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceMetadata() { return sourceMetadata; }
    public void setSourceMetadata(String sourceMetadata) { this.sourceMetadata = sourceMetadata; }
    public String getLastSyncRunId() { return lastSyncRunId; }
    public void setLastSyncRunId(String lastSyncRunId) { this.lastSyncRunId = lastSyncRunId; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getCurrentFileId() { return currentFileId; }
    public void setCurrentFileId(String currentFileId) { this.currentFileId = currentFileId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
