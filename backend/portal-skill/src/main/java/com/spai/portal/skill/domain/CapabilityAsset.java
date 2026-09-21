package com.spai.portal.skill.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "capability_asset")
public class CapabilityAsset {
    @Id private String id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String kind;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "current_version_id") private String currentVersionId;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; } public void setSlug(String slug) { this.slug = slug; }
    public String getKind() { return kind; } public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getCurrentVersionId() { return currentVersionId; } public void setCurrentVersionId(String currentVersionId) { this.currentVersionId = currentVersionId; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
