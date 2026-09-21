package com.spai.portal.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.asset.domain.*;
import com.spai.portal.asset.repository.*;
import com.spai.portal.asset.service.AssetService;
import com.spai.portal.common.BusinessException;
import com.spai.portal.integration.domain.*;
import com.spai.portal.integration.dto.ImaBridgeDtos.*;
import com.spai.portal.integration.repository.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImaBridgeService {
    private static final String SOURCE = "IMA_BRIDGE";
    private final SyncJobRepository jobs;
    private final ImaBridgeRunItemRepository runItems;
    private final AssetRepository assets;
    private final AssetCategoryRepository categories;
    private final CaseCategoryRepository caseCategories;
    private final FileObjectRepository files;
    private final ObjectMapper json;

    public ImaBridgeService(SyncJobRepository jobs, ImaBridgeRunItemRepository runItems, AssetRepository assets,
        AssetCategoryRepository categories, CaseCategoryRepository caseCategories, FileObjectRepository files, ObjectMapper json) {
        this.jobs = jobs; this.runItems = runItems; this.assets = assets; this.categories = categories;
        this.caseCategories = caseCategories; this.files = files; this.json = json;
    }

    @Transactional
    public SyncJob start(StartRequest request, String userId) {
        Optional<SyncJob> running = jobs.findFirstBySourceAndStatusOrderByStartedAtDesc(SOURCE, "RUNNING");
        if (running.isPresent()) {
            SyncJob existing = running.get();
            if (existing.getStartedAt().isAfter(OffsetDateTime.now().minusHours(12))) {
                throw BusinessException.conflict("已有本地 IMA 摆渡任务正在执行，批次号：" + existing.getId());
            }
            existing.setStatus("FAILED");
            existing.setErrorCount(1);
            existing.setMessage("超过 12 小时未完成，已由新批次自动终止");
            existing.setFinishedAt(OffsetDateTime.now());
            jobs.saveAndFlush(existing);
        }
        SyncJob job = new SyncJob();
        job.setId(UUID.randomUUID().toString());
        job.setSource(SOURCE);
        job.setStatus("RUNNING");
        job.setSourceReference(trim(request.knowledgeBaseId));
        job.setSourceShareId(trim(request.shareId));
        job.setStartedBy(userId);
        job.setMessage("本地摆渡同步中");
        return jobs.save(job);
    }

    @Transactional
    public BatchResult upsert(String runId, BatchRequest request) {
        SyncJob job = running(runId);
        int created = 0;
        int updated = 0;
        Set<String> batchMediaIds = new HashSet<String>();
        for (Item item : request.items) {
            item.mediaId = trim(item.mediaId);
            if (!batchMediaIds.add(item.mediaId)) continue;
            Asset asset = assets.findByMediaId(item.mediaId).orElse(null);
            boolean isNew = asset == null;
            if (isNew) { asset = new Asset(); asset.setId(UUID.randomUUID().toString()); }
            AssetCategory category = category(item);
            asset.setMediaId(item.mediaId);
            asset.setName(trim(item.name));
            asset.setType(item.type);
            asset.setStageId(item.stageId);
            asset.setCategoryId(category.getId());
            asset.setCaseCategoryId("C".equals(item.type) ? caseCategory(item, asset.getName()) : null);
            String fileId = fileId(item);
            if (fileId != null) asset.setCurrentFileId(fileId);
            asset.setSourceUrl(imaUrl(job.getSourceShareId(), item.mediaId));
            asset.setSourceType(SOURCE);
            asset.setSourceMetadata(metadata(item));
            asset.setLastSyncRunId(runId);
            asset.setLastSyncedAt(OffsetDateTime.now());
            asset.setStatus("PUBLISHED");
            asset.setEnabled(true);
            asset.setSearchText(asset.getName() + " " + item.categoryName + " " + item.stageId);
            assets.save(asset);

            if (!runItems.findByRunIdAndMediaId(runId, item.mediaId).isPresent()) {
                ImaBridgeRunItem marker = new ImaBridgeRunItem();
                marker.setId(UUID.randomUUID().toString()); marker.setRunId(runId);
                marker.setMediaId(item.mediaId); marker.setAssetId(asset.getId());
                runItems.save(marker);
            }
            if (isNew) created++; else updated++;
        }
        long count = runItems.countByRunId(runId);
        job.setTotalCount(safeInt(count));
        job.setSuccessCount(safeInt(count));
        job.setMessage("已接收 " + count + " 条 IMA 元数据");
        jobs.save(job);
        return new BatchResult(runId, batchMediaIds.size(), created, updated, count);
    }

    @Transactional
    public SyncJob complete(String runId) {
        SyncJob job = running(runId);
        long count = runItems.countByRunId(runId);
        if (count == 0) throw BusinessException.conflict("同步批次没有任何元数据，禁止完成空批次");
        int disabled = assets.disableMissingImaBridgeAssets(runId);
        job.setStatus("SUCCESS");
        job.setTotalCount(safeInt(count));
        job.setSuccessCount(safeInt(count));
        job.setMessage("本地 IMA 摆渡完成，共 " + count + " 条，停用缺失数据 " + disabled + " 条");
        job.setFinishedAt(OffsetDateTime.now());
        return jobs.save(job);
    }

    @Transactional
    public SyncJob fail(String runId, FailRequest request) {
        SyncJob job = running(runId);
        job.setStatus("FAILED");
        job.setErrorCount(1);
        job.setMessage(request == null || blank(request.message) ? "本地摆渡程序报告同步失败" : trim(request.message));
        job.setFinishedAt(OffsetDateTime.now());
        return jobs.save(job);
    }

    private SyncJob running(String runId) {
        SyncJob job = jobs.findById(runId).orElseThrow(() -> BusinessException.notFound("同步批次不存在"));
        if (!SOURCE.equals(job.getSource())) throw BusinessException.badRequest("该批次不是 IMA 本地摆渡批次");
        if (!"RUNNING".equals(job.getStatus())) throw BusinessException.conflict("同步批次已结束，当前状态：" + job.getStatus());
        return job;
    }

    private AssetCategory category(Item item) {
        String folderId = trim(item.folderId);
        Optional<AssetCategory> existing = blank(folderId) ? Optional.<AssetCategory>empty() : categories.findByExternalFolderId(folderId);
        AssetCategory category = existing.orElseGet(() -> categories.findFirstByStageIdAndName(item.stageId, trim(item.categoryName)).orElse(new AssetCategory()));
        if (category.getId() == null) category.setId(UUID.randomUUID().toString());
        category.setStageId(item.stageId);
        category.setName(trim(item.categoryName));
        category.setNumber(blank(item.categoryNumber) ? "99" : trim(item.categoryNumber));
        category.setExternalFolderId(blank(folderId) ? null : folderId);
        category.setSortOrder(parseOrder(category.getNumber()));
        return categories.save(category);
    }

    private String caseCategory(Item item, String assetName) {
        if (blank(item.caseCategoryName)) return AssetService.classifyCase(assetName);
        String folderId = trim(item.caseCategoryFolderId);
        Optional<CaseCategory> existing = blank(folderId) ? Optional.<CaseCategory>empty() : caseCategories.findByExternalFolderId(folderId);
        CaseCategory category = existing.orElseGet(() -> caseCategories.findByName(trim(item.caseCategoryName)).orElse(new CaseCategory()));
        if (category.getId() == null) {
            category.setId(UUID.randomUUID().toString());
            category.setCode("ima-" + category.getId().substring(0, 8));
            category.setDescription("由本地 IMA 摆渡程序同步维护。");
            category.setSortOrder(50);
        }
        category.setName(trim(item.caseCategoryName));
        category.setExternalFolderId(blank(folderId) ? null : folderId);
        category.setEnabled(true);
        return caseCategories.save(category).getId();
    }

    private String fileId(Item item) {
        if (blank(item.fileId)) return null;
        FileObject file = files.findById(trim(item.fileId))
            .orElseThrow(() -> BusinessException.badRequest("摆渡文件不存在：" + item.fileId));
        if (!"ACTIVE".equals(file.getStatus())) throw BusinessException.badRequest("摆渡文件不可用：" + item.fileId);
        return file.getId();
    }

    private String metadata(Item item) {
        try {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("mediaId", item.mediaId); value.put("folderId", item.folderId);
            value.put("caseCategoryFolderId", item.caseCategoryFolderId); value.put("ima", item.metadata);
            String serialized = json.writeValueAsString(value);
            if (serialized.length() > 65535) throw BusinessException.badRequest("IMA 元数据不能超过 64 KB：" + item.mediaId);
            return serialized;
        } catch (JsonProcessingException error) {
            throw BusinessException.badRequest("IMA 元数据格式无效：" + item.mediaId);
        }
    }

    private String imaUrl(String shareId, String mediaId) {
        try {
            return "https://ima.qq.com/wiki/?shareId=" + URLEncoder.encode(shareId, StandardCharsets.UTF_8.name())
                + "&action=autoOpenMedia&mediaId=" + URLEncoder.encode(mediaId, StandardCharsets.UTF_8.name());
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private int parseOrder(String value) { try { return Integer.parseInt(value); } catch (Exception error) { return 99; } }
    private int safeInt(long value) { return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value; }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
