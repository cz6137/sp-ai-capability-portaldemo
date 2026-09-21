package com.spai.portal.skill.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "skill_version")
public class SkillVersion {
    @Id private String id;
    @Column(name = "skill_id", nullable = false) private String skillId;
    @Column(name = "version_name", nullable = false) private String versionName;
    @Column(name = "file_id") private String fileId;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "change_note") private String changeNote;
    @Column(name = "review_note") private String reviewNote;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
