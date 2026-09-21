package com.spai.portal.integration.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "ima_bridge_run_item", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "media_id"}))
public class ImaBridgeRunItem {
    @Id private String id;
    @Column(name = "run_id", nullable = false) private String runId;
    @Column(name = "media_id", nullable = false, length = 500) private String mediaId;
    @Column(name = "asset_id", nullable = false, length = 36) private String assetId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getRunId(){return runId;} public void setRunId(String runId){this.runId=runId;}
    public String getMediaId(){return mediaId;} public void setMediaId(String mediaId){this.mediaId=mediaId;}
    public String getAssetId(){return assetId;} public void setAssetId(String assetId){this.assetId=assetId;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime createdAt){this.createdAt=createdAt;}
}
