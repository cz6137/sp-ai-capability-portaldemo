package com.spai.portal.integration.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "sync_job")
public class SyncJob {
    @Id private String id;
    @Column(nullable = false) private String source;
    @Column(nullable = false) private String status;
    @Column(name = "total_count", nullable = false) private int totalCount;
    @Column(name = "success_count", nullable = false) private int successCount;
    @Column(name = "error_count", nullable = false) private int errorCount;
    private String message;
    @Column(name = "source_reference", length = 500) private String sourceReference;
    @Column(name = "source_share_id", length = 500) private String sourceShareId;
    @Column(name = "started_by", length = 36) private String startedBy;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt = OffsetDateTime.now();
    @Column(name = "finished_at") private OffsetDateTime finishedAt;

    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getSource(){return source;} public void setSource(String source){this.source=source;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getTotalCount(){return totalCount;} public void setTotalCount(int totalCount){this.totalCount=totalCount;}
    public int getSuccessCount(){return successCount;} public void setSuccessCount(int successCount){this.successCount=successCount;}
    public int getErrorCount(){return errorCount;} public void setErrorCount(int errorCount){this.errorCount=errorCount;}
    public String getMessage(){return message;} public void setMessage(String message){this.message=message;}
    public String getSourceReference(){return sourceReference;} public void setSourceReference(String sourceReference){this.sourceReference=sourceReference;}
    public String getSourceShareId(){return sourceShareId;} public void setSourceShareId(String sourceShareId){this.sourceShareId=sourceShareId;}
    public String getStartedBy(){return startedBy;} public void setStartedBy(String startedBy){this.startedBy=startedBy;}
    public OffsetDateTime getStartedAt(){return startedAt;} public void setStartedAt(OffsetDateTime startedAt){this.startedAt=startedAt;}
    public OffsetDateTime getFinishedAt(){return finishedAt;} public void setFinishedAt(OffsetDateTime finishedAt){this.finishedAt=finishedAt;}
}
