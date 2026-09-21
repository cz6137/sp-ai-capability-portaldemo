package com.spai.portal.skill.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "capability_version", uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "version_name"}))
public class CapabilityVersion {
    @Id private String id;
    @Column(name = "asset_id", nullable = false) private String assetId;
    @Column(name = "version_name", nullable = false) private String versionName;
    @Column(name = "manifest_json", nullable = false, columnDefinition = "text") private String manifestJson;
    @Column(name = "package_file_id") private String packageFileId;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "change_note") private String changeNote;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getAssetId() { return assetId; } public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getVersionName() { return versionName; } public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getManifestJson() { return manifestJson; } public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
    public String getPackageFileId() { return packageFileId; } public void setPackageFileId(String packageFileId) { this.packageFileId = packageFileId; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getChangeNote() { return changeNote; } public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
