package com.spai.portal.integration.service;

import com.spai.portal.asset.domain.*;
import com.spai.portal.asset.repository.*;
import com.spai.portal.asset.service.AssetService;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.integration.domain.SyncJob;
import com.spai.portal.integration.repository.SyncJobRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SyncService {
    private static final long LOCK_ID = 2026081801L;
    private final SyncJobRepository jobs;
    private final AssetRepository assets;
    private final AssetCategoryRepository categories;
    private final CaseCategoryRepository caseCategories;
    private final FileStorageService fileStorage;
    private final JdbcTemplate jdbc;
    private final RestTemplate http;
    private final boolean enabled;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final String knowledgeBaseId;

    public SyncService(SyncJobRepository jobs, AssetRepository assets, AssetCategoryRepository categories,
        CaseCategoryRepository caseCategories, FileStorageService fileStorage, JdbcTemplate jdbc, RestTemplateBuilder rest,
        @Value("${portal.integration.remote-enabled:false}") boolean enabled,
        @Value("${portal.integration.ima.base-url:https://ima.qq.com}") String baseUrl,
        @Value("${portal.integration.ima.client-id:}") String clientId,
        @Value("${portal.integration.ima.api-key:}") String apiKey,
        @Value("${portal.integration.ima.knowledge-base-id:}") String knowledgeBaseId) {
        this.jobs = jobs;
        this.assets = assets;
        this.categories = categories;
        this.caseCategories = caseCategories;
        this.fileStorage = fileStorage;
        this.jdbc = jdbc;
        this.http = rest.setConnectTimeout(java.time.Duration.ofSeconds(15)).setReadTimeout(java.time.Duration.ofSeconds(45)).build();
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public SyncJob trigger(String source) {
        SyncJob job = start(source);
        if (!"IMA".equalsIgnoreCase(source)) return finish(job, "SKIPPED", "该数据源适配器尚未启用", 0, 0, 0);
        if (!enabled || blank(clientId) || blank(apiKey) || blank(knowledgeBaseId)) {
            return finish(job, "SKIPPED", "请配置 IMA Client ID、API Key 与知识库 ID 后启用远程同步", 0, 0, 0);
        }
        Boolean locked = jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, LOCK_ID);
        if (!Boolean.TRUE.equals(locked)) return finish(job, "SKIPPED", "已有 IMA 同步任务正在执行", 0, 0, 0);
        try {
            SyncResult result = syncIma();
            return finish(job, "SUCCESS", "IMA 知识库同步完成", result.total, result.saved, 0);
        } catch (RuntimeException error) {
            return finish(job, "FAILED", safeMessage(error), 0, 0, 1);
        } finally {
            jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean.class, LOCK_ID);
        }
    }

    @Scheduled(cron = "${portal.integration.ima-cron:0 0 2 * * *}")
    public void ima() { if (enabled) trigger("IMA"); }

    @Scheduled(cron = "${portal.integration.tencent-cron:0 30 2 * * *}")
    public void tencent() { if (enabled) trigger("TENCENT_DOCS"); }

    private SyncResult syncIma() {
        Set<String> activeMediaIds = new HashSet<String>();
        SyncResult result = new SyncResult();
        for (Map<String, Object> root : listFolder(null)) {
            if (!folder(root)) continue;
            String title = text(root.get("title"));
            String folderId = text(root.get("media_id"));
            if (title.contains("标杆案例") || title.contains("案例归纳")) syncCaseRoot(folderId, activeMediaIds, result);
            else {
                Integer stage = stageNumber(title);
                if (stage != null) syncStage(stage, folderId, activeMediaIds, result);
            }
        }
        for (Asset asset : assets.findBySourceType("IMA_SERVER")) {
            if (!activeMediaIds.contains(asset.getMediaId())) { asset.setEnabled(false); assets.save(asset); }
        }
        return result;
    }

    private void syncStage(Integer stage, String stageFolderId, Set<String> active, SyncResult result) {
        for (Map<String, Object> item : listFolder(stageFolderId)) {
            if (folder(item)) {
                String folderId = text(item.get("media_id"));
                AssetCategory category = stageCategory(stage, text(item.get("title")), folderId);
                syncFiles(stage, category, folderId, null, active, result);
            } else {
                AssetCategory category = stageCategory(stage, "未分类", stageFolderId + ":loose");
                saveRemoteFile(stage, category, null, item, active, result);
            }
        }
    }

    private void syncCaseRoot(String rootId, Set<String> active, SyncResult result) {
        for (Map<String, Object> item : listFolder(rootId)) {
            if (!folder(item)) continue;
            String folderId = text(item.get("media_id"));
            CaseCategory business = businessCategory(text(item.get("title")), folderId);
            for (Map<String, Object> child : listFolder(folderId)) {
                if (folder(child)) {
                    String nestedId = text(child.get("media_id"));
                    syncFiles(8, stageCategory(8, "标杆案例归纳", rootId), nestedId, business, active, result);
                } else saveRemoteFile(inferStage(text(child.get("title"))), stageCategory(8, "标杆案例归纳", rootId), business, child, active, result);
            }
        }
    }

    private void syncFiles(Integer stage, AssetCategory category, String folderId, CaseCategory business,
        Set<String> active, SyncResult result) {
        for (Map<String, Object> file : listFolder(folderId)) {
            if (folder(file)) syncFiles(stage, category, text(file.get("media_id")), business, active, result);
            else saveRemoteFile(stage, category, business, file, active, result);
        }
    }

    private void saveRemoteFile(Integer stage, AssetCategory category, CaseCategory business, Map<String, Object> remote,
        Set<String> active, SyncResult result) {
        String mediaId = text(remote.get("media_id"));
        if (blank(mediaId)) return;
        String rawTitle = text(remote.get("title"));
        String name = displayName(rawTitle);
        String type = business == null ? assetType(rawTitle) : "C";
        Asset asset = assets.findByMediaId(mediaId).orElse(new Asset());
        if (asset.getId() == null) asset.setId(UUID.randomUUID().toString());
        asset.setMediaId(mediaId);
        asset.setName(name);
        asset.setType(type);
        asset.setStageId(stage == null ? 8 : stage);
        asset.setCategoryId(category.getId());
        asset.setCaseCategoryId("C".equals(type) ? (business == null ? AssetService.classifyCase(name) : business.getId()) : null);
        String directUrl = mediaUrl(mediaId);
        asset.setSourceUrl(directUrl);
        asset.setSourceType("IMA_SERVER");
        asset.setLastSyncedAt(OffsetDateTime.now());
        if (!blank(directUrl)) asset.setCurrentFileId(downloadRemote(directUrl, name).getId());
        asset.setSearchText(name + " " + category.getName());
        asset.setStatus("PUBLISHED");
        asset.setEnabled(true);
        assets.save(asset);
        active.add(mediaId);
        result.total++;
        result.saved++;
    }

    private AssetCategory stageCategory(Integer stage, String title, String folderId) {
        Optional<AssetCategory> byFolder = categories.findByExternalFolderId(folderId);
        if (byFolder.isPresent()) {
            AssetCategory category = byFolder.get();
            category.setName(stripNumber(title));
            return categories.save(category);
        }
        String name = stripNumber(title);
        AssetCategory category = categories.findFirstByStageIdAndName(stage, name).orElse(new AssetCategory());
        if (category.getId() == null) category.setId(UUID.randomUUID().toString());
        category.setStageId(stage);
        category.setName(name);
        category.setNumber(numberPrefix(title));
        category.setExternalFolderId(folderId);
        category.setSortOrder(parseOrder(title));
        return categories.save(category);
    }

    private CaseCategory businessCategory(String title, String folderId) {
        Optional<CaseCategory> byFolder = caseCategories.findByExternalFolderId(folderId);
        if (byFolder.isPresent()) return byFolder.get();
        String name = stripNumber(title);
        CaseCategory category = caseCategories.findByName(name).orElse(new CaseCategory());
        if (category.getId() == null) {
            category.setId(UUID.randomUUID().toString());
            category.setCode("ima-" + category.getId().substring(0, 8));
            category.setDescription("由 IMA 标杆案例归纳文件夹同步维护。");
            category.setSortOrder(50);
        }
        category.setName(name);
        category.setExternalFolderId(folderId);
        category.setEnabled(true);
        return caseCategories.save(category);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listFolder(String folderId) {
        List<Map<String, Object>> all = new ArrayList<Map<String, Object>>();
        String cursor = "";
        for (int page = 0; page < 30; page++) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("knowledge_base_id", knowledgeBaseId);
            body.put("cursor", cursor);
            body.put("limit", 50);
            if (!blank(folderId)) body.put("folder_id", folderId);
            Map<String, Object> response = imaPost("openapi/wiki/v1/get_knowledge_list", body);
            if (number(response.get("code")) != 0) throw new IllegalStateException("IMA 列表读取失败: " + text(response.get("msg")));
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> pageItems = data == null ? null : (List<Map<String, Object>>) data.get("knowledge_list");
            if (pageItems != null) all.addAll(pageItems);
            if (data == null || Boolean.TRUE.equals(data.get("is_end"))) break;
            cursor = text(data.get("next_cursor"));
            if (blank(cursor)) break;
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private String mediaUrl(String mediaId) {
        Map<String, Object> response = imaPost("openapi/wiki/v1/get_media_info", Collections.<String, Object>singletonMap("media_id", mediaId));
        if (number(response.get("code")) != 0) return null;
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> urlInfo = data == null ? null : (Map<String, Object>) data.get("url_info");
        return urlInfo == null ? null : text(urlInfo.get("url"));
    }

    private FileObject downloadRemote(String url, String fileName) {
        return http.execute(url, HttpMethod.GET, null, response -> {
            MediaType mediaType = response.getHeaders().getContentType();
            return fileStorage.store(response.getBody(), fileName, mediaType == null ? "application/octet-stream" : mediaType.toString());
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> imaPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("ima-openapi-clientid", clientId);
        headers.set("ima-openapi-apikey", apiKey);
        ResponseEntity<Map> response = http.exchange(baseUrl + "/" + path, HttpMethod.POST, new HttpEntity<Map<String, Object>>(body, headers), Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new IllegalStateException("IMA 接口请求失败");
        return (Map<String, Object>) response.getBody();
    }

    private SyncJob start(String source) {
        SyncJob job = new SyncJob();
        job.setId(UUID.randomUUID().toString());
        job.setSource(source.toUpperCase(Locale.ROOT));
        job.setStatus("RUNNING");
        return jobs.save(job);
    }

    private SyncJob finish(SyncJob job, String status, String message, int total, int success, int errors) {
        job.setStatus(status);
        job.setMessage(message);
        job.setTotalCount(total);
        job.setSuccessCount(success);
        job.setErrorCount(errors);
        job.setFinishedAt(OffsetDateTime.now());
        return jobs.save(job);
    }

    private String assetType(String title) {
        if (title.contains(" - C - ")) return "C";
        if (title.contains(" - A - ") || title.toLowerCase(Locale.ROOT).contains("skill")) return "A";
        return "T";
    }
    private String displayName(String title) { String[] parts = title.split(" - "); return parts.length >= 4 ? String.join(" - ", Arrays.copyOfRange(parts, 3, parts.length)) : title; }
    private boolean folder(Map<String, Object> item) { return number(item.get("media_type")) == 99 || "FOLDER".equalsIgnoreCase(text(item.get("media_type"))); }
    private Integer stageNumber(String title) { for (int i = 0; i <= 8; i++) if (title.startsWith(String.valueOf(i)) || title.contains(i + "-")) return i; if (title.contains("项目监控")) return 0; if (title.contains("预投立项")) return 1; return null; }
    private Integer inferStage(String title) { Integer stage = stageNumber(title); return stage == null ? 8 : stage; }
    private String stripNumber(String value) { return value == null ? "" : value.replaceFirst("^\\d+[-_]?\\s*", "").trim(); }
    private String numberPrefix(String value) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(value == null ? "" : value); return m.find() ? m.group(1) : "99"; }
    private int parseOrder(String value) { try { return Integer.parseInt(numberPrefix(value)); } catch (NumberFormatException error) { return 99; } }
    private int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : -1; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String safeMessage(RuntimeException error) { String value = error.getMessage(); return value == null ? "IMA 同步失败" : value.substring(0, Math.min(value.length(), 1900)); }
    private static class SyncResult { int total; int saved; }
}
