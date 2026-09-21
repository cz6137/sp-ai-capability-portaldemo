package com.spai.portal.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.audit.domain.AuditLog;
import com.spai.portal.audit.repository.AuditLogRepository;
import com.spai.portal.common.BusinessException;
import com.spai.portal.skill.domain.CapabilityAsset;
import com.spai.portal.skill.domain.CapabilityVersion;
import com.spai.portal.skill.repository.CapabilityAssetRepository;
import com.spai.portal.skill.repository.CapabilityVersionRepository;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapabilityManagementServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void adminContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-admin", "unused", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @org.junit.jupiter.api.AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private Map<String, CapabilityAsset> assetData;
    private Map<String, CapabilityVersion> versionData;
    private List<AuditLog> auditData;
    private FileStorageService files;
    private CapabilityManagementService service;

    @BeforeEach void setUp() {
        assetData = new LinkedHashMap<>(); versionData = new LinkedHashMap<>(); auditData = new ArrayList<>();
        CapabilityAssetRepository assets = mock(CapabilityAssetRepository.class);
        CapabilityVersionRepository versions = mock(CapabilityVersionRepository.class);
        AuditLogRepository audits = mock(AuditLogRepository.class); files = mock(FileStorageService.class);
        when(assets.save(any())).thenAnswer(call -> { CapabilityAsset a = call.getArgument(0); assetData.put(a.getId(), a); return a; });
        when(assets.lockBySlug(anyString())).thenAnswer(call -> assetData.values().stream().filter(a -> a.getSlug().equals(call.getArgument(0))).findFirst());
        when(assets.findBySlug(anyString())).thenAnswer(call -> assetData.values().stream().filter(a -> a.getSlug().equals(call.getArgument(0))).findFirst());
        when(assets.lockById(anyString())).thenAnswer(call -> Optional.ofNullable(assetData.get(call.getArgument(0))));
        when(assets.findById(anyString())).thenAnswer(call -> Optional.ofNullable(assetData.get(call.getArgument(0))));
        when(assets.findAllByOrderByUpdatedAtDesc()).thenAnswer(call -> new ArrayList<>(assetData.values()));
        when(assets.findByStatusOrderByUpdatedAtDesc(anyString())).thenAnswer(call -> assetData.values().stream().filter(a -> a.getStatus().equals(call.getArgument(0))).collect(Collectors.toList()));
        when(versions.save(any())).thenAnswer(call -> { CapabilityVersion v = call.getArgument(0); versionData.put(v.getId(), v); return v; });
        when(versions.findById(anyString())).thenAnswer(call -> Optional.ofNullable(versionData.get(call.getArgument(0))));
        when(versions.findByAssetIdAndVersionName(anyString(), anyString())).thenAnswer(call -> versionData.values().stream().filter(v -> v.getAssetId().equals(call.getArgument(0)) && v.getVersionName().equals(call.getArgument(1))).findFirst());
        when(versions.findByAssetIdOrderByCreatedAtDesc(anyString())).thenAnswer(call -> versionData.values().stream().filter(v -> v.getAssetId().equals(call.getArgument(0))).sorted(Comparator.comparing(CapabilityVersion::getCreatedAt).reversed()).collect(Collectors.toList()));
        when(audits.save(any())).thenAnswer(call -> { AuditLog a = call.getArgument(0); auditData.add(a); return a; });
        when(audits.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(anyString(), anyString())).thenAnswer(call -> auditData.stream().filter(a -> a.getTargetType().equals(call.getArgument(0)) && a.getTargetId().equals(call.getArgument(1))).sorted(Comparator.comparing(AuditLog::getCreatedAt).reversed()).collect(Collectors.toList()));
        when(files.store(any(org.springframework.web.multipart.MultipartFile.class))).thenAnswer(call -> { FileObject f = new FileObject(); f.setId(UUID.randomUUID().toString()); return f; });
        service = new CapabilityManagementService(json, assets, versions, files, audits);
    }

    @Test void importsAreDraftOnlyAndCannotClaimReview() throws Exception {
        ObjectNode root = manifest("1.0.0"); governance(root).put("status", "PUBLISHED").put("reviewer", "forged");
        assertThrows(BusinessException.class, () -> service.importManifest(root.toString(), null, "author", null));
        assertTrue(assetData.isEmpty()); verifyNoInteractions(files);
        governance(root).put("status", "DRAFT");
        Map<String, Object> saved = service.importManifest(root.toString(), null, "author", null);
        assertFalse(((JsonNode)saved.get("manifest")).path("governance").has("reviewer"));
        assertEquals("DRAFT", saved.get("status")); assertEquals("author", auditData.get(0).getActorId());
    }

    @Test void draftAndReviewNeverReplacePublishedVersionOrDownload() throws Exception {
        Map<String, Object> published = publishFirst();
        String oldManifest = versionData.get(published.get("versionId")).getManifestJson();
        Map<String, Object> draft = create("2.0.0", "creator");
        act(draft, "submit", "creator");
        assertEquals("1.0.0", service.published("local-tool").get("version"));
        assertEquals(oldManifest, versionData.get(published.get("versionId")).getManifestJson());
        service.download("local-tool"); verify(files).load((String) published.get("packageFileId"));
        assertEquals(2, service.history((String) draft.get("id")).size());
        assertEquals(1L, service.list().get(0).get("pendingReviews"));
    }

    @Test void creatorEditorAndSubmitterCannotReviewOwnVersion() throws Exception {
        Map<String, Object> record = create("1.0.0", "creator");
        // 只有资产所有者能修改并提交自己的草稿；即使同一个账号既创建、修改又提交，也不能审核自己的版本。
        ObjectNode edited = manifest("1.0.0"); ((ObjectNode)edited.path("identity")).put("tagline", "由创建者修改后的草稿说明");
        Map<String, Object> draft = service.importManifest(edited.toString(), zipForManifest(edited.toString()), "creator", record.get("updatedAt").toString());
        // 另一位账号不能接管他人创建的能力；下面的职责分离判断以此为前提。
        assertThrows(BusinessException.class, () -> service.importManifest(edited.toString(), zipForManifest(edited.toString()), "editor", draft.get("updatedAt").toString()));
        assertThrows(BusinessException.class, () -> act(draft, "submit", "editor"));
        final Map<String, Object> review = act(draft, "submit", "creator");
        // 提交审计必须记录真实提交账号，审核独立性判断依赖它，而不是 Manifest 中的文字。
        AuditLog submitAudit = auditData.get(auditData.size() - 1);
        assertEquals("CAPABILITY_SUBMIT", submitAudit.getAction());
        assertEquals("creator", submitAudit.getActorId());
        assertEquals(review.get("versionId"), submitAudit.getTargetId());
        // 创建、修改、提交该版本的账号不能审核自己的内容。
        BusinessException error = assertThrows(BusinessException.class, () -> act(review, "approve", "creator"));
        assertEquals("FORBIDDEN", error.getCode());
        // 没有参与该版本的其他管理员可以独立审核，并据此发布依据。
        assertEquals(Boolean.TRUE, act(review, "approve", "reviewer").get("reviewApproved"));
    }

    @Test void ordinaryEmployeeCannotGovernOrSubmitCapabilities() throws Exception {
        Map<String, Object> draft = create("1.0.0", "owner");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("employee", "unused", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))));
        for (String action : Arrays.asList("submit", "approve", "publish", "unpublish", "archive")) {
            BusinessException error = assertThrows(BusinessException.class, () -> act(draft, action, "employee"));
            assertEquals("FORBIDDEN", error.getCode());
        }
        assertTrue(service.myList("employee").isEmpty());
        assertEquals("DRAFT", versionData.get(draft.get("versionId")).getStatus());
    }

    @Test void createAndSubmitAcceptOnlyRealPackageArchives() throws Exception {
        String text = manifest("1.0.0").toString();
        assertThrows(BusinessException.class, () -> service.importManifest(text, new MockMultipartFile("package", "伪包.zip", "application/zip", new byte[]{1}), "creator", null));
        assertThrows(BusinessException.class, () -> service.importManifest(text, new MockMultipartFile("package", "旧格式.tar", "application/x-tar", new byte[]{1}), "creator", null));
        assertTrue(assetData.isEmpty()); verifyNoInteractions(files);
    }

    @Test void referencesMustBeNonEmptyTextLocations() throws Exception {
        ObjectNode numeric = manifest("1.0.0"); ((ObjectNode)numeric).putObject("references").put("manual", 7);
        assertThrows(BusinessException.class, () -> service.importManifest(numeric.toString(), null, "creator", null));
        ObjectNode empty = manifest("1.0.0"); ((ObjectNode)empty).putObject("references").put("manual", "   ");
        assertThrows(BusinessException.class, () -> service.importManifest(empty.toString(), null, "creator", null));
        verifyNoInteractions(files);
    }

    @Test void publishRequiresActualApprovalNotManifestReviewText() throws Exception {
        Map<String, Object> record = act(create("1.0.0", "creator"), "submit", "creator");
        CapabilityVersion stored = versionData.get(record.get("versionId"));
        ObjectNode root = (ObjectNode)json.readTree(stored.getManifestJson()); governance(root).put("reviewer", "forged").put("reviewedAt", "2026-09-05"); stored.setManifestJson(root.toString());
        assertThrows(BusinessException.class, () -> act(record, "publish", "publisher"));
        assertTrue(service.publishedList(null).isEmpty());
    }

    @Test void rejectionClearsApprovalAndRequiresAnotherReview() throws Exception {
        Map<String, Object> review = act(create("1.0.0", "creator"), "submit", "creator");
        Map<String, Object> approved = act(review, "approve", "reviewer");
        Map<String, Object> rejected = act(approved, "reject", "reviewer");
        assertEquals("DRAFT", rejected.get("status"));
        assertFalse(((JsonNode)rejected.get("manifest")).path("governance").has("reviewer"));
        Map<String, Object> resubmitted = act(rejected, "submit", "creator");
        assertThrows(BusinessException.class, () -> act(resubmitted, "publish", "publisher"));
    }

    @Test void reviewingPublishedAndArchivedVersionsAreImmutable() throws Exception {
        Map<String, Object> record = create("1.0.0", "creator");
        Map<String, Object> review = act(record, "submit", "creator");
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "creator", review.get("updatedAt").toString()));
        Map<String, Object> published = act(act(review, "approve", "reviewer"), "publish", "publisher");
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "creator", published.get("updatedAt").toString()));
        Map<String, Object> archived = act(act(published, "unpublish", "publisher"), "archive", "publisher");
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "creator", archived.get("updatedAt").toString()));
    }

    @Test void staleEditsAndActionsAreRejected() throws Exception {
        Map<String, Object> old = create("1.0.0", "creator");
        Map<String, Object> updated = service.importManifest(manifest("1.0.0").toString(), null, "creator", old.get("updatedAt").toString());
        assertNotEquals(old.get("updatedAt"), updated.get("updatedAt"));
        assertThrows(BusinessException.class, () -> act(old, "submit", "creator"));
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "creator", old.get("updatedAt").toString()));
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "creator", null));
    }

    @Test void publishSwitchesPointerAndRetainsOldVersion() throws Exception {
        Map<String, Object> first = publishFirst();
        Map<String, Object> second = act(act(act(create("2.0.0", "creator"), "submit", "creator"), "approve", "reviewer"), "publish", "publisher");
        assertEquals("2.0.0", service.published("local-tool").get("version"));
        assertEquals("PUBLISHED", versionData.get(first.get("versionId")).getStatus());
        assertThrows(BusinessException.class, () -> act(first, "unpublish", "publisher"));
        assertThrows(BusinessException.class, () -> act(second, "archive", "publisher"));
    }

    @Test void unpublishBlocksPublicAccessButKeepsHistoricalPackage() throws Exception {
        Map<String, Object> first = publishFirst();
        Map<String, Object> withdrawn = act(first, "unpublish", "publisher");
        assertEquals("PUBLISHED", withdrawn.get("status")); assertEquals(Boolean.FALSE, withdrawn.get("currentlyPublished"));
        assertThrows(BusinessException.class, () -> service.published("local-tool"));
        assertThrows(BusinessException.class, () -> service.download("local-tool"));
        assertTrue(service.publishedList("tool").isEmpty());
        service.adminDownload((String)first.get("id"), (String)first.get("versionId"));
        verify(files).load((String) first.get("packageFileId"));
        Map<String, Object> archived = act(withdrawn, "archive", "publisher");
        assertEquals("ARCHIVED", archived.get("status"));
        assertThrows(BusinessException.class, () -> act(archived, "publish", "publisher"));
    }

    @Test void versionIdentifiersCannotCrossAssets() throws Exception {
        Map<String, Object> record = create("1.0.0", "creator");
        assertThrows(BusinessException.class, () -> service.adminDownload("another-asset", (String)record.get("versionId")));
        assertThrows(BusinessException.class, () -> service.auditHistory("another-asset", (String)record.get("versionId")));
    }

    @Test void auditHasActualActorReasonAndPackageWithoutFullManifest() throws Exception {
        Map<String, Object> record = publishFirst();
        List<AuditLog> logs = service.auditHistory((String)record.get("id"), (String)record.get("versionId"));
        assertEquals(4, logs.size()); assertEquals("CAPABILITY_PUBLISH", logs.get(0).getAction());
        assertEquals("publisher", logs.get(0).getActorId());
        JsonNode after = json.readTree(logs.get(0).getAfterData());
        assertEquals("本地单元测试意见", after.path("reason").asText());
        assertEquals(record.get("packageFileId"), after.path("packageFileId").asText());
        assertFalse(after.has("manifest"));
        assertEquals("reviewer", ((JsonNode)record.get("manifest")).path("governance").path("reviewer").asText());
    }

    @Test void missingOpinionsActorOrDeliveryPackageAreRejected() throws Exception {
        ObjectNode root = manifest("1.0.0"); ((ObjectNode)root.path("delivery")).put("mode", "hybrid");
        ((ObjectNode)root.path("delivery")).set("package", json.readTree("{\"environment\":\"本地\",\"installGuide\":\"按手册\",\"packageItems\":[{\"name\":\"manual.md\",\"detail\":\"手册\"}]}"));
        assertThrows(BusinessException.class, () -> service.importManifest(root.toString(), null, "creator", null));
        assertThrows(BusinessException.class, () -> service.importManifest(manifest("1.0.0").toString(), null, "", null));
        Map<String, Object> draft = create("1.0.0", "creator");
        assertThrows(BusinessException.class, () -> service.transition((String)draft.get("id"), (String)draft.get("versionId"), "submit", " ", draft.get("updatedAt").toString(), "creator"));
    }

    @Test void malformedBusinessRowsAndUnknownRuntimeHandlersFailServerPrecheck() throws Exception {
        ObjectNode invalidRows = manifest("1.0.0"); ((ObjectNode)invalidRows.path("usage")).set("inputs", json.readTree("[null]"));
        assertThrows(BusinessException.class, () -> service.importManifest(invalidRows.toString(), null, "creator", null));
        ObjectNode invalidRuntime = manifest("1.0.0");
        invalidRuntime.set("runtime", json.readTree("{\"renderer\":\"server-job\",\"executionMode\":\"server-job\",\"handler\":\"unknown\",\"route\":\"/tools/example\",\"fileLimitMB\":20,\"supportNotes\":[],\"operations\":[],\"securityStatement\":\"本地测试\"}"));
        assertThrows(BusinessException.class, () -> service.importManifest(invalidRuntime.toString(), null, "creator", null));
        verifyNoInteractions(files);
    }

    @Test void allCurrentSourceManifestsPassServerPrecheckAsDrafts() throws Exception {
        java.nio.file.Path root = java.nio.file.Paths.get("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isDirectory(root.resolve("capabilities"))) root = root.getParent();
        assertNotNull(root, "此合同测试需要完整项目工作目录");
        int count = 0;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> source = java.nio.file.Files.newDirectoryStream(root.resolve("capabilities"))) {
            for (java.nio.file.Path directory : source) {
                java.nio.file.Path path = directory.resolve("capability.json"); if (!java.nio.file.Files.isRegularFile(path)) continue;
                String manifest = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                Map<String, Object> record = service.importManifest(manifest, zipForManifest(manifest), "test-importer", null);
                assertEquals("DRAFT", record.get("status")); count++;
            }
        }
        assertEquals(4, count); assertTrue(service.publishedList(null).isEmpty());
    }

    @Test void publishedAdapterIsNotInPublicCatalog() throws Exception {
        ObjectNode root = manifest("1.0.0");
        ((ObjectNode) root.path("identity")).put("slug", "platform-skill-adapter");
        Map<String, Object> saved = service.importManifest(root.toString(), null, "creator", null);
        act(act(act(saved, "submit", "creator"), "approve", "reviewer"), "publish", "publisher");
        assertTrue(service.publishedList(null).isEmpty());
        assertEquals(1, service.list().size());
    }
    private MockMultipartFile zipForManifest(String manifest) throws Exception {
        JsonNode root = json.readTree(manifest); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        zip.putNextEntry(new ZipEntry("capability.json")); zip.write(manifest.getBytes("UTF-8")); zip.closeEntry();
        Set<String> entries = new HashSet<String>();
        for (JsonNode item : root.path("delivery").path("package").path("packageItems")) {
            String path = item.path("ref").asText(""); if (path.isEmpty()) path = item.path("name").asText("");
            addZipEntry(zip, entries, path);
        }
        JsonNode references = root.path("references");
        if (references.isArray()) for (JsonNode reference : references) {
            String path = reference.asText(""); if (!path.matches("(?i)^https?://.*")) addZipEntry(zip, entries, path);
        }
        if ("skill".equals(root.path("kind").asText())) addZipEntry(zip, entries, "SKILL.md");
        zip.close(); return new MockMultipartFile("package", "test.zip", "application/zip", bytes.toByteArray());
    }
    private void addZipEntry(ZipOutputStream zip, Set<String> entries, String path) throws Exception {
        if (path.isEmpty() || !entries.add(path)) return;
        zip.putNextEntry(new ZipEntry(path)); zip.write("test".getBytes("UTF-8")); zip.closeEntry();
    }
    private Map<String, Object> create(String version, String actor) throws Exception {
        String text = manifest(version).toString();
         return service.importManifest(text, zipForManifest(text), actor, null);
    }
    private Map<String, Object> publishFirst() throws Exception { return act(act(act(create("1.0.0", "creator"), "submit", "creator"), "approve", "reviewer"), "publish", "publisher"); }
    private Map<String, Object> act(Map<String, Object> record, String action, String actor) {
        return service.transition((String)record.get("id"), (String)record.get("versionId"), action, "本地单元测试意见", record.get("updatedAt").toString(), actor);
    }
    private ObjectNode governance(ObjectNode root) { return (ObjectNode)root.path("governance"); }
    private ObjectNode manifest(String version) throws Exception {
        ObjectNode root = (ObjectNode)json.readTree("{\"schemaVersion\":\"2.1\",\"kind\":\"tool\",\"identity\":{\"slug\":\"local-tool\",\"name\":\"本地测试工具\",\"tagline\":\"用于验证平台治理流程\",\"description\":\"这是一份用于验证能力治理行为的合法测试清单。\",\"version\":\"1.0\",\"maintainer\":\"测试维护人\",\"updatedAt\":\"2026-09-05\"},\"classification\":{\"stageIds\":[],\"tags\":[],\"audiences\":[]},\"usage\":{\"scenarios\":[\"测试\"],\"inputs\":[{\"name\":\"输入\",\"detail\":\"材料\"}],\"outputs\":[{\"name\":\"输出\",\"detail\":\"结果\"}],\"workflow\":[{\"title\":\"检查\",\"detail\":\"人工核对\"}],\"quickStart\":[\"准备\"]},\"quality\":{\"dimensions\":[{\"name\":\"准确\",\"criteria\":\"一致\",\"evidence\":\"原文\"}],\"humanReview\":[\"人工确认\"],\"boundaries\":[\"测试数据\"]},\"delivery\":{\"mode\":\"online\",\"online\":{\"enabled\":false,\"acceptedExtensions\":[],\"externalProviders\":[],\"maxFileSizeMB\":20,\"processingLocation\":\"本地\",\"retentionPolicy\":\"不留存\",\"dataNotice\":\"仅测试\"}},\"governance\":{\"status\":\"DRAFT\",\"changeNote\":\"单元测试\"},\"references\":{}}");
        ((ObjectNode)root.path("identity")).put("version", version); return root;
    }
}
