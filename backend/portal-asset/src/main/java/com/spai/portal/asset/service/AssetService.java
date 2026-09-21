package com.spai.portal.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.asset.domain.*;
import com.spai.portal.asset.repository.*;
import com.spai.portal.common.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {
    private static final String REAL_ESTATE = "10000000-0000-0000-0000-000000000001";
    private static final String SPATIAL = "10000000-0000-0000-0000-000000000002";
    private static final String NATURAL_RESOURCES = "10000000-0000-0000-0000-000000000003";
    private static final String SURVEYING = "10000000-0000-0000-0000-000000000004";
    private static final String GENERAL = "10000000-0000-0000-0000-000000000005";

    private final AssetRepository assets;
    private final AssetCategoryRepository categories;
    private final CaseCategoryRepository caseCategories;
    private final DeliveryStageRepository stages;
    private final AssetVersionRepository versions;
    private final ObjectMapper json;

    public AssetService(AssetRepository assets, AssetCategoryRepository categories, CaseCategoryRepository caseCategories,
        DeliveryStageRepository stages, AssetVersionRepository versions, ObjectMapper json) {
        this.assets = assets;
        this.categories = categories;
        this.caseCategories = caseCategories;
        this.stages = stages;
        this.versions = versions;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> stages() {
        List<Asset> all = assets.search(null, null, null);
        return stages.findAllByOrderBySortOrderAsc().stream().map(s -> {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("stage", s.getId());
            value.put("name", s.getId() + "-" + s.getName());
            value.put("description", s.getDescription());
            value.put("counts", counts(all, s.getId()));
            value.put("subcategories", categories.findByStageIdOrderBySortOrderAsc(s.getId()));
            return value;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(Integer stage, String type, String query) {
        Map<String, AssetCategory> categoryMap = categories.findAll().stream().collect(Collectors.toMap(AssetCategory::getId, c -> c));
        Map<Integer, DeliveryStage> stageMap = stages.findAll().stream().collect(Collectors.toMap(DeliveryStage::getId, s -> s));
        return assets.search(stage, blank(type), blank(query)).stream()
            .map(a -> dto(a, categoryMap.get(a.getCategoryId()), stageMap.get(a.getStageId()))).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        Asset asset = assets.findById(id).orElseThrow(() -> BusinessException.notFound("资产不存在"));
        Map<String, Object> result = dto(asset, categories.findById(asset.getCategoryId()).orElse(null), stages.findById(asset.getStageId()).orElse(null));
        result.put("versions", versions.findByAssetIdOrderByVersionNoDesc(id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> caseCategories() {
        return caseCategories.findByEnabledTrueOrderBySortOrderAsc().stream().map(category -> {
            List<Asset> items = assets.findPublishedCases(category.getId());
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("id", category.getId());
            value.put("code", category.getCode());
            value.put("name", category.getName());
            value.put("description", category.getDescription());
            value.put("externalFolderId", category.getExternalFolderId());
            value.put("count", items.size());
            value.put("examples", items.stream().limit(3).map(Asset::getName).collect(Collectors.toList()));
            return value;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cases(String code) {
        CaseCategory category = caseCategories.findByCode(code).orElseThrow(() -> BusinessException.notFound("案例分类不存在"));
        Map<String, AssetCategory> categoryMap = categories.findAll().stream().collect(Collectors.toMap(AssetCategory::getId, c -> c));
        Map<Integer, DeliveryStage> stageMap = stages.findAll().stream().collect(Collectors.toMap(DeliveryStage::getId, s -> s));
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("category", category);
        value.put("items", assets.findPublishedCases(category.getId()).stream()
            .map(a -> dto(a, categoryMap.get(a.getCategoryId()), stageMap.get(a.getStageId()))).collect(Collectors.toList()));
        return value;
    }

    @Transactional
    public AssetVersion transition(String versionId, String action, String note, String userId) {
        AssetVersion version = versions.findById(versionId).orElseThrow(() -> BusinessException.notFound("版本不存在"));
        String next;
        if ("submit".equals(action) && "DRAFT".equals(version.getStatus())) next = "IN_REVIEW";
        else if ("approve".equals(action) && "IN_REVIEW".equals(version.getStatus())) next = "APPROVED";
        else if ("publish".equals(action) && "APPROVED".equals(version.getStatus())) next = "PUBLISHED";
        else if ("reject".equals(action) && ("IN_REVIEW".equals(version.getStatus()) || "APPROVED".equals(version.getStatus()))) next = "DRAFT";
        else throw BusinessException.conflict("当前状态不允许此操作");
        version.setStatus(next);
        version.setReviewNote(note);
        versions.save(version);
        if ("PUBLISHED".equals(next)) {
            Asset asset = assets.findById(version.getAssetId()).orElseThrow(() -> BusinessException.notFound("资产不存在"));
            asset.setStatus("PUBLISHED");
            asset.setCurrentFileId(version.getFileId());
            assets.save(asset);
        }
        return version;
    }

    public static String classifyCase(String name) {
        String value = name == null ? "" : name;
        if (containsAny(value, "不动产", "房产", "确权登记")) return REAL_ESTATE;
        if (containsAny(value, "时空", "GIS", "一张图", "城市大脑")) return SPATIAL;
        if (containsAny(value, "自然资源", "国土空间", "用途管制", "政务服务")) return NATURAL_RESOURCES;
        if (containsAny(value, "测绘", "地籍", "遥感", "调查")) return SURVEYING;
        return GENERAL;
    }

    private static boolean containsAny(String value, String... keys) {
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }

    private Map<String, Object> counts(List<Asset> all, Integer stage) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("sd_template", all.stream().filter(a -> stage.equals(a.getStageId()) && "T".equals(a.getType())).count());
        value.put("case", all.stream().filter(a -> stage.equals(a.getStageId()) && "C".equals(a.getType())).count());
        value.put("ai_tool", all.stream().filter(a -> stage.equals(a.getStageId()) && "A".equals(a.getType())).count());
        return value;
    }

    private Map<String, Object> dto(Asset asset, AssetCategory category, DeliveryStage stage) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", asset.getId());
        value.put("name", asset.getName());
        value.put("type", asset.getType());
        value.put("stage_num", asset.getStageId());
        value.put("stage_name", stage == null ? String.valueOf(asset.getStageId()) : asset.getStageId() + "-" + stage.getName());
        value.put("sub_name", category == null ? "" : category.getName());
        value.put("sub_number", category == null ? "" : category.getNumber());
        value.put("media_id", asset.getMediaId());
        value.put("file_id", asset.getCurrentFileId());
        value.put("source_url", asset.getSourceUrl());
        value.put("source_type", asset.getSourceType());
        value.put("source_metadata", sourceMetadata(asset.getSourceMetadata()));
        value.put("last_synced_at", asset.getLastSyncedAt());
        value.put("case_category_id", asset.getCaseCategoryId());
        return value;
    }

    private String blank(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private Object sourceMetadata(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return json.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception error) { return value; }
    }
}
