package com.spai.portal.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spai.portal.audit.domain.AuditLog;
import com.spai.portal.audit.repository.AuditLogRepository;
import java.time.temporal.ChronoUnit;
import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.common.BusinessException;
import com.spai.portal.skill.domain.CapabilityAsset;
import com.spai.portal.skill.domain.CapabilityVersion;
import com.spai.portal.skill.repository.CapabilityAssetRepository;
import com.spai.portal.skill.repository.CapabilityVersionRepository;
import java.time.OffsetDateTime;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CapabilityManagementService {
    private static final Set<String> KINDS = new HashSet<String>(Arrays.asList("skill", "tool"));
    private static final Set<String> STATUSES = new HashSet<String>(Arrays.asList("DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED"));
    private final ObjectMapper json;
    private final CapabilityAssetRepository assets;
    private final CapabilityVersionRepository versions;
    private final FileStorageService files;
    private final AuditLogRepository audits;
    private final CapabilityManifestSchemaValidator manifestValidator;
    private static final String AUDIT_TYPE = "CAPABILITY_VERSION";

    public CapabilityManagementService(ObjectMapper json, CapabilityAssetRepository assets, CapabilityVersionRepository versions, FileStorageService files, AuditLogRepository audits) {
        this.json = json; this.assets = assets; this.versions = versions; this.files = files; this.audits = audits;
        this.manifestValidator = new CapabilityManifestSchemaValidator(json);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CapabilityAsset asset : assets.findAllByOrderByUpdatedAtDesc()) {
            Map<String, Object> item = view(asset);
            long pending = versions.findByAssetIdOrderByCreatedAtDesc(asset.getId()).stream()
                .filter(v -> "IN_REVIEW".equals(v.getStatus()) && !approved(auditTrail(v.getId()))).count();
            item.put("pendingReviews", pending);
            result.add(item);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, String kind, String submitter) {
        if (!blank(status) && !STATUSES.contains(status) && !"APPROVED".equals(status)) throw BusinessException.badRequest("status 无效");
        if (!blank(kind) && !KINDS.contains(kind)) throw BusinessException.badRequest("kind 必须为 skill 或 tool");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CapabilityAsset asset : assets.findAllByOrderByUpdatedAtDesc()) {
            if (!blank(kind) && !kind.equals(asset.getKind())) continue;
            for (CapabilityVersion version : versions.findByAssetIdOrderByCreatedAtDesc(asset.getId())) {
                List<AuditLog> trail = auditTrail(version.getId());
                boolean approved = "IN_REVIEW".equals(version.getStatus()) && approved(trail);
                String queueStatus = approved ? "APPROVED" : version.getStatus();
                if (!blank(status) && !status.equals(queueStatus)) continue;
                AuditLog submission = trail.stream().filter(log -> "CAPABILITY_SUBMIT".equals(log.getAction())).findFirst().orElse(null);
                String submitterId = submission == null ? version.getCreatedBy() : submission.getActorId();
                if (!blank(submitter) && !submitter.equals(submitterId)) continue;
                Map<String, Object> item = view(asset, version);
                item.put("submitterId", submitterId);
                item.put("submittedAt", submission == null ? null : submission.getCreatedAt());
                item.put("pendingReviews", "IN_REVIEW".equals(queueStatus) ? 1 : 0);
                result.add(item);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> publishedList(String kind) {
        if (!blank(kind) && !KINDS.contains(kind)) throw BusinessException.badRequest("kind 必须为 skill 或 tool");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CapabilityAsset asset : assets.findByStatusOrderByUpdatedAtDesc("PUBLISHED")) {
            if ("platform-skill-adapter".equals(asset.getSlug())) continue;
            if (blank(kind) || kind.equals(asset.getKind())) result.add(publishedView(asset));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> published(String slug) {
        return publishedView(assetBySlug(slug));
    }

    @Transactional(readOnly = true)
    public FileStorageService.StoredFile download(String slug) {
        CapabilityAsset asset = assetBySlug(slug);
        publishedView(asset);
        return packageFor(versionFor(asset.getId(), asset.getCurrentVersionId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myList(String userId) {
        requireActor(userId); List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CapabilityAsset asset : assets.findByCreatedByOrderByUpdatedAtDesc(userId)) result.add(view(asset));
        return result;
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myHistory(String assetId, String userId) { requireOwner(assetId, userId); return history(assetId); }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myAudit(String assetId, String versionId, String userId) {
        requireOwner(assetId, userId); List<Map<String,Object>> out=new ArrayList<Map<String,Object>>();
        for (AuditLog l : auditHistory(assetId, versionId)) { Map<String,Object> m=new LinkedHashMap<String,Object>(); m.put("id",l.getId());m.put("action",l.getAction());m.put("actorId",l.getActorId());m.put("createdAt",l.getCreatedAt()); try { JsonNode a=parse(l.getAfterData()); m.put("afterData",Collections.singletonMap("reason",a.path("reason").asText(""))); } catch(Exception e) { } out.add(m); } return out;
    }
    @Transactional(readOnly = true)
    public FileStorageService.StoredFile myDownload(String assetId, String versionId, String userId) { requireOwner(assetId,userId); return adminDownload(assetId,versionId); }
    private void requireOwner(String assetId, String userId) { requireActor(userId); CapabilityAsset a=assets.findById(assetId).orElseThrow(() -> BusinessException.notFound("能力资产不存在")); if (!userId.equals(a.getCreatedBy())) throw BusinessException.forbidden("无权访问该能力"); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String assetId) {
        CapabilityAsset asset = assets.findById(assetId).orElseThrow(() -> BusinessException.notFound("能力资产不存在"));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CapabilityVersion version : versions.findByAssetIdOrderByCreatedAtDesc(assetId)) result.add(view(asset, version));
        return result;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> auditHistory(String assetId, String versionId) {
        versionFor(assetId, versionId);
        return auditTrail(versionId);
    }

    @Transactional(readOnly = true)
    public FileStorageService.StoredFile adminDownload(String assetId, String versionId) {
        return packageFor(versionFor(assetId, versionId));
    }

    @Transactional
    public Map<String, Object> importManifest(String manifestText, MultipartFile upload, String userId, String expectedUpdatedAt) {
        requireActor(userId);
        JsonNode root = parse(manifestText);
        require("DRAFT".equals(text(root.path("governance"), "status")), "导入只能保存草稿；审核和发布请使用独立操作");
        validate(root);
        clearReview(root);
        JsonNode identity = root.path("identity");
        String slug = text(identity, "slug");
        String kind = text(root, "kind");
        String versionName = text(identity, "version");
        CapabilityAsset asset = assets.lockBySlug(slug).orElse(null);
        boolean created = asset == null;
        if (created) {
            asset = new CapabilityAsset(); asset.setId(UUID.randomUUID().toString()); asset.setSlug(slug);
            asset.setKind(kind); asset.setCreatedBy(userId); asset.setCreatedAt(now());
        } else if (!asset.getKind().equals(kind)) {
            throw BusinessException.conflict("已存在的能力不能在 Skill 和工具之间切换");
        } else if (!userId.equals(asset.getCreatedBy())) {
            throw BusinessException.forbidden("无权接管其他员工的能力");
        } else if ("platform-skill-adapter".equals(slug)) {
            throw BusinessException.forbidden("该适配器仅管理员可操作");
        }
        CapabilityVersion version = created ? null : versions.findByAssetIdAndVersionName(asset.getId(), versionName).orElse(null);
        String before = version == null ? null : snapshot(asset, version);
        if (version != null) {
            if (!"DRAFT".equals(version.getStatus())) throw BusinessException.conflict("只有草稿可以修改；其他状态请创建新版本");
            checkRevision(version, expectedUpdatedAt);
        }
        String packageFileId = version == null ? null : version.getPackageFileId();
        if (upload != null && !upload.isEmpty()) {
            validateUpload(root, upload);
            FileObject stored = files.store(upload); packageFileId = stored.getId();
        }
        requirePackage(root, packageFileId);
        OffsetDateTime now = nextTime(version);
        if (created) { asset.setName(text(identity, "name")); asset.setUpdatedAt(now); assets.save(asset); }
        if (version == null) {
            version = new CapabilityVersion(); version.setId(UUID.randomUUID().toString()); version.setAssetId(asset.getId());
            version.setVersionName(versionName); version.setCreatedBy(userId); version.setCreatedAt(now);
        }
        version.setManifestJson(encode(root)); version.setPackageFileId(packageFileId); version.setStatus("DRAFT");
        version.setChangeNote(text(root.path("governance"), "changeNote")); version.setUpdatedAt(now); versions.save(version);
        // current_version_id is the public pointer while the asset is published.
        if (!"PUBLISHED".equals(asset.getStatus())) {
            asset.setCurrentVersionId(version.getId()); asset.setName(text(identity, "name")); asset.setStatus("DRAFT");
        }
        asset.setUpdatedAt(now); assets.save(asset);
        record("IMPORT", asset, version, userId, version.getChangeNote(), before);
        return view(asset, version);
    }

    @Transactional
    public Map<String, Object> transition(String assetId, String versionId, String action, String reason, String expectedUpdatedAt, String userId) {
        requireActor(userId);
        boolean admin = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!"submit".equals(action) && !admin) throw BusinessException.forbidden("仅管理员可执行该操作");
        require(!blank(reason) && reason.trim().length() <= 1000, "请填写不超过 1000 字的操作意见");
        CapabilityAsset asset = assets.lockById(assetId).orElseThrow(() -> BusinessException.notFound("能力资产不存在"));
        if ("submit".equals(action) && !userId.equals(asset.getCreatedBy())) throw BusinessException.forbidden("无权提交该能力");
        CapabilityVersion version = versionFor(assetId, versionId);
        checkRevision(version, expectedUpdatedAt);
        JsonNode root = parse(version.getManifestJson());
        String before = snapshot(asset, version);
        String status = version.getStatus();
        List<AuditLog> trail = auditTrail(versionId);
        switch (action == null ? "" : action) {
            case "submit":
                requireState("DRAFT".equals(status), "只有草稿可以提交审核");
                validate(root); requirePackage(root, version.getPackageFileId()); clearReview(root);
                version.setStatus("IN_REVIEW");
                break;
            case "approve":
                requireState("IN_REVIEW".equals(status) && !approved(trail), "只有待审核版本可以审核通过");
                requireIndependentReviewer(version, trail, userId);
                ((ObjectNode) root.path("governance")).put("reviewer", userId).put("reviewedAt", now().toLocalDate().toString());
                break;
            case "reject":
                requireState("IN_REVIEW".equals(status), "只有审核中的版本可以退回");
                requireIndependentReviewer(version, trail, userId);
                version.setStatus("DRAFT"); clearReview(root);
                break;
            case "publish":
                requireState("IN_REVIEW".equals(status) && approved(trail), "发布前须由独立审核人审核通过");
                validate(root); requirePackage(root, version.getPackageFileId());
                requireState(!blank(text(root.path("governance"), "reviewer")) && !blank(text(root.path("governance"), "reviewedAt")), "缺少真实审核记录");
                version.setStatus("PUBLISHED"); asset.setCurrentVersionId(versionId); asset.setStatus("PUBLISHED");
                asset.setName(text(root.path("identity"), "name"));
                break;
            case "unpublish":
                requireState(currentlyPublished(asset, version), "只有当前发布版本可以下架");
                // Retain the immutable published version for history and package traceability.
                asset.setStatus("ARCHIVED");
                break;
            case "archive":
                requireState(("DRAFT".equals(status) || "PUBLISHED".equals(status)) && !currentlyPublished(asset, version), "归档前请退回审核或先下架当前发布版本");
                version.setStatus("ARCHIVED");
                break;
            default:
                throw BusinessException.badRequest("不支持的治理操作");
        }
        ((ObjectNode) root.path("governance")).put("status", version.getStatus());
        version.setManifestJson(encode(root)); version.setUpdatedAt(nextTime(version)); versions.save(version);
        if (!"PUBLISHED".equals(asset.getStatus()) && versionId.equals(asset.getCurrentVersionId()) && !"unpublish".equals(action)) {
            asset.setStatus(version.getStatus());
        }
        asset.setUpdatedAt(now()); assets.save(asset);
        record(action.toUpperCase(java.util.Locale.ROOT), asset, version, userId, reason.trim(), before);
        return view(asset, version);
    }

    private Map<String, Object> view(CapabilityAsset asset) { return view(asset, versionFor(asset.getId(), asset.getCurrentVersionId())); }

    private Map<String, Object> publishedView(CapabilityAsset asset) {
        CapabilityVersion version = versionFor(asset.getId(), asset.getCurrentVersionId());
        if (!currentlyPublished(asset, version)) throw BusinessException.notFound("能力资产尚未发布");
        return view(asset, version);
    }

    private Map<String, Object> view(CapabilityAsset asset, CapabilityVersion version) {
        JsonNode manifest = parse(version.getManifestJson());
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", asset.getId()); value.put("versionId", version.getId()); value.put("slug", asset.getSlug());
        value.put("kind", asset.getKind()); value.put("name", text(manifest.path("identity"), "name"));
        value.put("version", version.getVersionName()); value.put("status", version.getStatus()); value.put("assetStatus", asset.getStatus());
        value.put("currentlyPublished", currentlyPublished(asset, version)); value.put("currentVersionId", asset.getCurrentVersionId());
        value.put("packageFileId", version.getPackageFileId()); value.put("createdBy", version.getCreatedBy());
        value.put("createdAt", version.getCreatedAt()); value.put("updatedAt", version.getUpdatedAt());
        value.put("reviewApproved", "IN_REVIEW".equals(version.getStatus()) && approved(auditTrail(version.getId())));
        value.put("manifest", manifest);
        return value;
    }

    private CapabilityAsset assetBySlug(String slug) {
        return assets.findBySlug(slug).orElseThrow(() -> BusinessException.notFound("能力资产不存在"));
    }
    private CapabilityVersion versionFor(String assetId, String versionId) {
        if (blank(versionId)) throw BusinessException.notFound("能力版本不存在");
        CapabilityVersion version = versions.findById(versionId).orElseThrow(() -> BusinessException.notFound("能力版本不存在"));
        if (!assetId.equals(version.getAssetId())) throw BusinessException.notFound("能力版本不存在");
        return version;
    }
    private FileStorageService.StoredFile packageFor(CapabilityVersion version) {
        if (blank(version.getPackageFileId())) throw BusinessException.notFound("该能力没有可下载的交付包");
        return files.load(version.getPackageFileId());
    }
    private static final long MAX_PACKAGE_BYTES = CapabilityZipValidator.MAX_TOTAL_BYTES;
    private static final long MAX_ENTRY_BYTES = CapabilityZipValidator.MAX_ENTRY_BYTES;

    private void validateUpload(JsonNode manifest, MultipartFile upload) {
        String name = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().trim();
        require(!name.isEmpty() && name.matches("(?i).+\\.(zip|tar|gz|jar)$"), "能力包扩展名必须为 zip、tar、gz 或 jar");
        require(upload.getSize() >= 0 && upload.getSize() <= MAX_PACKAGE_BYTES, "能力包大小超过 500 MB 限制");
        // Only ZIP has a safe static inspection implementation. Do not persist unverifiable archives.
        require(name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"), "当前仅支持可静态检查的 ZIP 能力包；tar、gz、jar 暂不接受");
        File temp = null;
        try {
            temp = File.createTempFile("portal-capability-", ".zip");
            upload.transferTo(temp);
            ZipDirectoryPolicy.validate(temp);
            inspectZip(manifest, temp);
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw BusinessException.badRequest("能力包 ZIP 无法读取或已损坏");
        } finally { if (temp != null && temp.exists()) temp.delete(); }
    }

    private void inspectZip(JsonNode manifest, File file) throws IOException {
        new CapabilityZipValidator(json).validate(manifest, file);
    }

    private void requirePackage(JsonNode root, String packageFileId) {
        String mode = text(root.path("delivery"), "mode");
        require(!("skill".equals(text(root, "kind")) || "package".equals(mode) || "hybrid".equals(mode)) || !blank(packageFileId),
            "该交付方式必须上传真实能力包或部署包");
    }
    private void clearReview(JsonNode root) { ((ObjectNode) root.path("governance")).remove(Arrays.asList("reviewer", "reviewedAt")); }
    private List<AuditLog> auditTrail(String versionId) { return audits.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AUDIT_TYPE, versionId); }
    private boolean approved(List<AuditLog> trail) { return !trail.isEmpty() && "CAPABILITY_APPROVE".equals(trail.get(0).getAction()); }
    private void requireIndependentReviewer(CapabilityVersion version, List<AuditLog> trail, String userId) {
        boolean contributor = userId.equals(version.getCreatedBy()) || trail.stream().anyMatch(log ->
            userId.equals(log.getActorId()) && Arrays.asList("CAPABILITY_IMPORT", "CAPABILITY_SUBMIT").contains(log.getAction()));
        if (contributor) throw BusinessException.forbidden("创建、修改或提交该版本的账号不能审核自己的内容，请由另一位管理员审核");
    }
    private void checkRevision(CapabilityVersion version, String expected) {
        if (blank(expected)) throw BusinessException.conflict("请重新读取版本后操作，缺少版本更新时间");
        try {
            if (!version.getUpdatedAt().isEqual(OffsetDateTime.parse(expected))) throw BusinessException.conflict("版本已被其他操作更新，请刷新后重试");
        } catch (java.time.format.DateTimeParseException error) { throw BusinessException.badRequest("版本更新时间格式无效"); }
    }
    private void record(String action, CapabilityAsset asset, CapabilityVersion version, String actor, String reason, String before) {
        AuditLog log = new AuditLog(); log.setId(UUID.randomUUID().toString()); log.setActorId(actor);
        log.setAction("CAPABILITY_" + action); log.setTargetType(AUDIT_TYPE); log.setTargetId(version.getId());
        log.setBeforeData(before);
        ObjectNode after = (ObjectNode) parse(snapshot(asset, version)); after.put("reason", reason);
        log.setAfterData(encode(after)); log.setCreatedAt(version.getUpdatedAt()); audits.save(log);
    }
    // Audit only governance identifiers and opinions, not full manifests, files or credentials.
    private String snapshot(CapabilityAsset asset, CapabilityVersion version) {
        ObjectNode result = json.createObjectNode();
        result.put("assetId", asset.getId()).put("version", version.getVersionName()).put("status", version.getStatus());
        result.put("assetStatus", asset.getStatus()).put("currentVersionId", asset.getCurrentVersionId());
        result.put("packageFileId", version.getPackageFileId()).put("updatedAt", version.getUpdatedAt().toString());
        return encode(result);
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception error) { throw BusinessException.badRequest("能力数据无法序列化"); }
    }
    private static void requireActor(String actor) { if (blank(actor)) throw BusinessException.forbidden("缺少登录操作账号"); }
    private static void requireState(boolean condition, String message) { if (!condition) throw BusinessException.conflict(message); }
    private static boolean currentlyPublished(CapabilityAsset asset, CapabilityVersion version) {
        return "PUBLISHED".equals(asset.getStatus()) && "PUBLISHED".equals(version.getStatus()) && version.getId().equals(asset.getCurrentVersionId());
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private static OffsetDateTime nextTime(CapabilityVersion version) {
        OffsetDateTime time = now();
        return version != null && !time.isAfter(version.getUpdatedAt()) ? version.getUpdatedAt().plusNanos(1000) : time;
    }

    private JsonNode parse(String value) {
        if (blank(value)) throw BusinessException.badRequest("缺少能力 JSON 清单");
        if (value.length() > 1024 * 1024) throw BusinessException.badRequest("能力 JSON 清单不能超过 1 MB");
        try { JsonNode root = json.readTree(value); if (root == null || !root.isObject()) throw BusinessException.badRequest("能力清单必须是 JSON 对象"); return root; }
        catch (BusinessException error) { throw error; }
        catch (Exception error) { throw BusinessException.badRequest("能力 JSON 清单解析失败"); }
    }

    private void validate(JsonNode root) {
        manifestValidator.validate(root);
        validatePlatformSemantics(root);
    }

    private void validatePlatformSemantics(JsonNode root) {
        JsonNode runtime = root.path("runtime");
        if (!runtime.isObject()) return;
        String slug = text(root.path("identity"), "slug");
        String renderer = text(runtime, "renderer");
        require(("/tools/" + slug).equals(text(runtime, "route")), "runtime.route 必须匹配能力标识");
        JsonNode online = root.path("delivery").path("online");
        if (online.isObject()) require(runtime.path("fileLimitMB").asInt() == online.path("maxFileSizeMB").asInt(), "runtime 与 online 的文件上限必须一致");
        if ("document-transform".equals(renderer)) {
            Set<String> ids = new HashSet<String>();
            for (JsonNode operation : runtime.path("operations")) {
                require(ids.add(text(operation, "id")), "operation.id 不能重复");
                require(Arrays.asList("markdown-to-docx", "markdown-to-pdf", "docx-to-markdown", "pdf-to-markdown", "image-to-markdown").contains(text(operation, "executor")), "平台未注册该转换执行器");
            }
        }
        if ("server-job".equals(renderer)) require("server-job".equals(text(runtime, "executionMode")) && "meeting-minutes-v1".equals(text(runtime, "handler")), "服务端处理器或运行方式未注册");
    }
    private static void require(boolean condition, String message) { if (!condition) throw BusinessException.badRequest(message); }
    private static String text(JsonNode node, String field) { return node == null ? "" : node.path(field).asText("").trim(); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
