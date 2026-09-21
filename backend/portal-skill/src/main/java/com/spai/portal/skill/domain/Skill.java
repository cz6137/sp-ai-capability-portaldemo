package com.spai.portal.skill.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.persistence.*;

@Entity
@Table(name = "skill")
public class Skill {
    @Id private String id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "owner_id") private String ownerId;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "download_count", nullable = false) private long downloadCount;
    @Column(name = "source_type", nullable = false) private String sourceType = "INTERNAL";
    @Column(name = "case_text", columnDefinition = "text") private String caseText;
    @Column(name = "usage_guide", columnDefinition = "text") private String usageGuide;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "skill_stage", joinColumns = @JoinColumn(name = "skill_id"))
    @Column(name = "stage_id")
    private Set<Integer> stageIds = new LinkedHashSet<Integer>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(long downloadCount) { this.downloadCount = downloadCount; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getCaseText() { return caseText; }
    public void setCaseText(String caseText) { this.caseText = caseText; }
    public String getUsageGuide() { return usageGuide; }
    public void setUsageGuide(String usageGuide) { this.usageGuide = usageGuide; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Set<Integer> getStageIds() { return stageIds; }
    public void setStageIds(Set<Integer> stageIds) { this.stageIds = stageIds == null ? new LinkedHashSet<Integer>() : stageIds; }
}
