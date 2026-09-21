package com.spai.portal.integration.dto;

import java.util.*;
import javax.validation.Valid;
import javax.validation.constraints.*;

public final class ImaBridgeDtos {
    private ImaBridgeDtos() {}

    public static class StartRequest {
        @NotBlank @Size(max = 500) public String knowledgeBaseId;
        @NotBlank @Size(max = 500) public String shareId;
    }

    public static class BatchRequest {
        @NotEmpty @Size(max = 200) @Valid public List<Item> items = new ArrayList<Item>();
    }

    public static class Item {
        @NotBlank @Size(max = 500) public String mediaId;
        @NotBlank @Size(max = 500) public String name;
        @NotBlank @Pattern(regexp = "T|C|A") public String type;
        @NotNull @Min(0) @Max(8) public Integer stageId;
        @NotBlank @Size(max = 300) public String categoryName;
        @Size(max = 20) public String categoryNumber;
        @Size(max = 200) public String folderId;
        @Size(max = 200) public String caseCategoryName;
        @Size(max = 200) public String caseCategoryFolderId;
        @Size(max = 36) public String fileId;
        public Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    }

    public static class FailRequest {
        @Size(max = 1800) public String message;
    }

    public static class BatchResult {
        public final String runId;
        public final int received;
        public final int created;
        public final int updated;
        public final long synchronizedCount;
        public BatchResult(String runId, int received, int created, int updated, long synchronizedCount) {
            this.runId = runId; this.received = received; this.created = created; this.updated = updated;
            this.synchronizedCount = synchronizedCount;
        }
    }
}
