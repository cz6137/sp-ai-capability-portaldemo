package com.spai.portal.skill.service;

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
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 交付包静态校验：包内 capability.json 与上传 Manifest 的关联、packageItems、本地 references、
 * Skill 的 SKILL.md，以及路径、重复条目、加密 ZIP 和特殊文件等安全检查。
 * 每项失败同时证明“校验发生在 files.store 之前”，失败包不会进入文件存储。
 */
class CapabilityPackageValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private Map<String, CapabilityAsset> assetData;
    private Map<String, CapabilityVersion> versionData;
    private FileStorageService files;
    private CapabilityManagementService service;

    @BeforeEach void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-admin", "unused",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assetData = new LinkedHashMap<String, CapabilityAsset>(); versionData = new LinkedHashMap<String, CapabilityVersion>();
        CapabilityAssetRepository assets = mock(CapabilityAssetRepository.class);
        CapabilityVersionRepository versions = mock(CapabilityVersionRepository.class);
        AuditLogRepository audits = mock(AuditLogRepository.class); files = mock(FileStorageService.class);
        when(assets.save(any())).thenAnswer(call -> { CapabilityAsset a = call.getArgument(0); assetData.put(a.getId(), a); return a; });
        when(assets.lockBySlug(anyString())).thenReturn(Optional.<CapabilityAsset>empty());
        when(assets.findBySlug(anyString())).thenReturn(Optional.<CapabilityAsset>empty());
        when(assets.lockById(anyString())).thenAnswer(call -> Optional.ofNullable(assetData.get(call.getArgument(0))));
        when(assets.findById(anyString())).thenAnswer(call -> Optional.ofNullable(assetData.get(call.getArgument(0))));
        when(assets.findAllByOrderByUpdatedAtDesc()).thenAnswer(call -> new ArrayList<CapabilityAsset>(assetData.values()));
        when(versions.save(any())).thenAnswer(call -> { CapabilityVersion v = call.getArgument(0); versionData.put(v.getId(), v); return v; });
        when(versions.findById(anyString())).thenAnswer(call -> Optional.ofNullable(versionData.get(call.getArgument(0))));
        when(versions.findByAssetIdOrderByCreatedAtDesc(anyString())).thenReturn(new ArrayList<CapabilityVersion>());
        when(audits.save(any())).thenAnswer(call -> call.getArgument(0));
        when(audits.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(anyString(), anyString())).thenReturn(new ArrayList<AuditLog>());
        when(files.store(any(org.springframework.web.multipart.MultipartFile.class))).thenAnswer(call -> { FileObject f = new FileObject(); f.setId(UUID.randomUUID().toString()); return f; });
        service = new CapabilityManagementService(json, assets, versions, files, audits);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void zipWithParentTraversalPathIsRejectedBeforeStorage() throws Exception {
        String manifest = zipPathManifest();
        assertRejectedAfterPackageCheck(entryPatchedZip("../secret.txt", "attack"), "能力包路径非法或重复", manifest);
    }

    @Test void zipWithAbsoluteAndDrivePathsIsRejectedBeforeStorage() throws Exception {
        String manifest = zipPathManifest();
        assertRejectedAfterPackageCheck(entryPatchedZip("/etc/hosts", "attack"), "能力包路径非法或重复", manifest);
        assertRejectedAfterPackageCheck(entryPatchedZip("C:/windows/win.ini", "attack"), "能力包路径非法或重复", manifest);
    }

    @Test void zipWithDuplicateEntryIsRejectedBeforeStorage() throws Exception {
        assertRejectedAfterPackageCheck(duplicateEntryZip(), "能力包路径非法或重复");
    }

    @Test void encryptedZipIsRejectedBeforeStorage() throws Exception {
        assertRejectedAfterPackageCheck(flagPatchedZip(), "加密");
    }

    @Test void zipWithLinkOrSpecialFileIsRejectedBeforeStorage() throws Exception {
        assertRejectedAfterPackageCheck(symlinkPatchedZip(), "链接和特殊文件");
    }

    @Test void packageWithoutRootCapabilityJsonIsRejectedBeforeStorage() throws Exception {
        assertRejectedAfterPackageCheck(zip(entries("SKILL.md"), null), "缺少根目录 capability.json");
    }

    @Test void corruptedArchiveIsRejectedBeforeStorage() throws Exception {
        String manifest = toolManifestWithExtraItem("SKILL.md");
        byte[] bytes = zip(namedEntries("manual.md", "references/quality-rules.md", "SKILL.md"), manifest);
        int target = -1;
        for (int i = 0; i + 30 <= bytes.length; i++) {
            if (!signature(bytes, i, 0x04034b50)) continue;
            String name = new String(bytes, i + 30, u16(bytes, i + 26), java.nio.charset.StandardCharsets.UTF_8);
            if ("manual.md".equals(name)) { target = i; break; }
        }
        assertTrue(target >= 0, "未找到待破坏的本地条目");
        // 翻转首个压缩数据字节，使解压或 CRC 校验必然失败，模拟损坏的交付包。
        bytes[target + 30 + u16(bytes, target + 26) + u16(bytes, target + 28)] ^= 0x5a;
        assertRejectedAfterPackageCheck(bytes, "无法读取或已损坏", manifest);
    }

    @Test void packageManifestMustMatchUploadedIdentity() throws Exception {
        String manifest = toolManifest();
        ObjectNode packaged = (ObjectNode) json.readTree(manifest);
        ((ObjectNode) packaged.path("identity")).put("version", "9.9");
        byte[] mismatched = zip(entries("manual.md", "references/quality-rules.md"), packaged.toString());
        assertRejectedAfterPackageCheck(mismatched, "包内清单与上传清单不一致");

        ObjectNode wrongKind = (ObjectNode) json.readTree(manifest);
        wrongKind.put("kind", "skill");
        assertRejectedAfterPackageCheck(zip(entries("manual.md", "references/quality-rules.md"), wrongKind.toString()), "包内清单与上传清单不一致");

        ObjectNode wrongSchema = (ObjectNode) json.readTree(manifest);
        wrongSchema.put("schemaVersion", "2.0");
        assertRejectedAfterPackageCheck(zip(entries("manual.md", "references/quality-rules.md"), wrongSchema.toString()), "包内清单与上传清单不一致");
    }

    @Test void declaredPackageItemMustExistInTheArchive() throws Exception {
        String manifest = toolManifest();
        byte[] missingItem = zip(entries("manual.md"), manifest);
        assertRejectedAfterPackageCheck(missingItem, "清单声明的包内文件不存在", manifest);
    }

    @Test void localReferenceMustExistWhileRemoteReferenceIsNotRequiredInPackage() throws Exception {
        String local = toolManifest().replace("https://example.invalid/manual", "references/detailed.md");
        assertRejectedAfterPackageCheck(zip(entries("manual.md", "references/quality-rules.md"), local), "本地参考文件不存在", local);
        importSucceeds(toolManifest(), zip(entries("manual.md", "references/quality-rules.md"), toolManifest()));
    }

    @Test void referenceValueMustBeNonEmptyText() throws Exception {
        // Manifest 自身的 references 必须是有效文字位置；这里不上传能力包，专门验证清单校验。
        String nonText = toolManifest().replace("\"https://example.invalid/manual\"", "7");
        assertRejectedAfterPackageCheck(nonText, null, "类型不符");
        String blank = toolManifest().replace("\"https://example.invalid/manual\"", "\"   \"");
        assertRejectedAfterPackageCheck(blank, null, "格式不符");
    }

    @Test void skillPackageMustCarryRootSkillFile() throws Exception {
        String manifest = skillManifest();
        byte[] withoutSkill = zip(entries("manual.md", "references/quality-rules.md", "CHANGELOG.md"), manifest);
        assertRejectedAfterPackageCheck(withoutSkill, "SKILL.md", manifest);
        importSucceeds(manifest, zip(entries("manual.md", "references/quality-rules.md", "CHANGELOG.md", "SKILL.md"), manifest));
    }

    @Test void rejectedPackageIsNotStoredAndAcceptedPackageIsStoredOnce() throws Exception {
        String manifest = toolManifest();
        assertRejectedAfterPackageCheck(entriesOnlyZip(), "references/quality-rules.md", manifest);
        Map<String, Object> saved = service.importManifest(manifest, upload(validZip()), "creator", null);
        verify(files, times(1)).store(any(org.springframework.web.multipart.MultipartFile.class));
        assertFalse(((String) saved.get("packageFileId")).isEmpty());
        assertEquals(1, assetData.size());
    }

    private void assertRejectedAfterPackageCheck(byte[] bytes, String expectedFragment) {
        assertRejectedAfterPackageCheck(bytes, expectedFragment, toolManifest());
    }

    private void assertRejectedAfterPackageCheck(byte[] bytes, String expectedFragment, String manifest) {
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.importManifest(manifest, upload(bytes), "creator", null));
        assertTrue(error.getMessage().contains(expectedFragment), "未预期的校验原因：" + error.getMessage());
        // 校验失败必须发生在文件存储之前，且不产生任何资产或版本记录。
        verifyNoInteractions(files);
        assertTrue(assetData.isEmpty()); assertTrue(versionData.isEmpty());
    }

    /** 清单校验失败时连能力包都不需要上传，直接证明拒绝发生在文件存储之前。 */
    private void assertRejectedAfterPackageCheck(String manifest, byte[] ignored, String expectedFragment) {
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.importManifest(manifest, null, "creator", null));
        assertTrue(error.getMessage().contains(expectedFragment), "未预期的校验原因：" + error.getMessage());
        verifyNoInteractions(files);
        assertTrue(assetData.isEmpty()); assertTrue(versionData.isEmpty());
    }

    private void importSucceeds(String manifest, byte[] bytes) throws Exception {
        Map<String, Object> saved = service.importManifest(manifest, upload(bytes), "creator", null);
        assertEquals("DRAFT", saved.get("status"));
    }

    private byte[] entriesOnlyZip() throws Exception { return zip(entries("manual.md"), toolManifest()); }

    private byte[] validZip() throws Exception { return zip(entries("manual.md", "references/quality-rules.md"), toolManifest()); }

    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("package", "capability-package.zip", "application/zip", bytes); }

    private Map<String, byte[]> entries(String... names) {
        Map<String, byte[]> values = new LinkedHashMap<String, byte[]>();
        for (String name : names) assertTrue(values.put(name, name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) == null, "测试用例条目重复：" + name);
        return values;
    }

    /** 与 entries 相同，但保留可读内容，便于校验损坏压缩流时的解压失败。 */
    private Map<String, byte[]> namedEntries(String... names) {
        Map<String, byte[]> values = new LinkedHashMap<String, byte[]>();
        for (String name : names) values.put(name, ("payload of " + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return values;
    }

    /** 生成合法 ZIP；capability.json 默认写入上传 Manifest 本身，可传入 null 或不一致内容模拟异常包。 */
    private byte[] zip(Map<String, byte[]> extra, String capabilityJson) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        Set<String> written = new LinkedHashSet<String>();
        if (capabilityJson != null) write(zip, written, "capability.json", capabilityJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> entry : extra.entrySet()) write(zip, written, entry.getKey(), entry.getValue());
        zip.close(); return bytes.toByteArray();
    }

    private void write(ZipOutputStream zip, Set<String> written, String name, byte[] content) throws Exception {
        if (!written.add(name)) return;
        zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
    }

    /** 合法包基础上追加一个违规条目。 */
    private byte[] entryPatchedZip(String name, String content) throws Exception {
        Map<String, byte[]> values = entries("manual.md", "references/quality-rules.md");
        values.put(name, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return zip(values, zipPathManifest());
    }

    /** 引用路径在清单里存在（放在 faq 字段），因此只有路径安全检查能拦住它。 */
    private String zipPathManifest() {
        return toolManifest().replace("\"faq\":\"https://example.invalid/manual\"", "\"faq\":\"references/quality-rules.md\"");
    }

    /** 在合法包的中央目录末尾复制一条 manual.md 记录，模拟包内重复路径。 */
    private byte[] duplicateEntryZip() throws Exception {
        byte[] bytes = zip(entries("manual.md", "references/quality-rules.md"), toolManifest());
        int end = centralDirectoryEnd(bytes);
        int first = -1;
        for (int i = 0; i + 46 <= bytes.length; i++) if (signature(bytes, i, 0x02014b50)) { first = i; break; }
        assertTrue(first >= 0, "未找到 ZIP 中央目录");
        int length = 46 + u16(bytes, first + 28) + u16(bytes, first + 30) + u16(bytes, first + 32);
        byte[] patched = new byte[bytes.length + length];
        System.arraycopy(bytes, 0, patched, 0, end);
        System.arraycopy(bytes, first, patched, end, length);
        System.arraycopy(bytes, end, patched, end + length, bytes.length - end);
        int endRecord = end + length;
        int count = u16(patched, endRecord + 10) + 1;
        patched[endRecord + 8] = (byte) (count & 0xff); patched[endRecord + 9] = (byte) ((count >>> 8) & 0xff);
        patched[endRecord + 10] = patched[endRecord + 8]; patched[endRecord + 11] = patched[endRecord + 9];
        int size = (int) u32(patched, endRecord + 12) + length;
        patched[endRecord + 12] = (byte) (size & 0xff); patched[endRecord + 13] = (byte) ((size >>> 8) & 0xff);
        patched[endRecord + 14] = (byte) ((size >>> 16) & 0xff); patched[endRecord + 15] = (byte) ((size >>> 24) & 0xff);
        return patched;
    }

    /** 把中央目录与本地条目标记为加密（通用位标记 bit0），模拟加密 ZIP。 */
    private byte[] flagPatchedZip() throws Exception { return patchEverySignature(entryPatchedZip("extra.md", "flag"), 8, 0x0001); }

    /** 把中央目录的外部属性高位改为符号链接（Unix 模式 0120777），模拟链接文件。 */
    private byte[] symlinkPatchedZip() throws Exception {
        byte[] bytes = entryPatchedZip("extra.md", "link");
        int patched = 0;
        for (int i = 0; i + 46 <= bytes.length; i++) {
            if (!signature(bytes, i, 0x02014b50)) continue;
            // 外部属性 4 字节小端：低 16 位是 DOS 属性，高 16 位是 Unix 模式。
            bytes[i + 38] = 0x00; bytes[i + 39] = 0x00; bytes[i + 40] = (byte) 0xff; bytes[i + 41] = (byte) 0xa1;
            patched++;
        }
        assertTrue(patched > 0, "未找到 ZIP 中央目录");
        return bytes;
    }

    private byte[] patchEverySignature(byte[] bytes, int offset, int flag) {
        int patched = 0;
        for (int i = 0; i + 4 <= bytes.length; i++) {
            if (signature(bytes, i, 0x04034b50) || signature(bytes, i, 0x02014b50)) {
                bytes[i + offset] = (byte) ((bytes[i + offset] | flag) & 0xff); patched++;
            }
        }
        assertTrue(patched > 0, "未找到可修改的 ZIP 条目标记");
        return bytes;
    }

    private static int centralDirectoryEnd(byte[] bytes) {
        for (int i = bytes.length - 22; i >= 0; i--) if (signature(bytes, i, 0x06054b50)) return i;
        throw new IllegalStateException("未找到 ZIP 目录结束记录");
    }

    private static int u16(byte[] bytes, int position) { return (bytes[position] & 0xff) | ((bytes[position + 1] & 0xff) << 8); }

    private static long u32(byte[] bytes, int position) { return u16(bytes, position) | ((long) u16(bytes, position + 2) << 16); }

    private static boolean signature(byte[] bytes, int position, int value) {
        return (bytes[position] & 0xff) == (value & 0xff)
            && (bytes[position + 1] & 0xff) == ((value >>> 8) & 0xff)
            && (bytes[position + 2] & 0xff) == ((value >>> 16) & 0xff)
            && (bytes[position + 3] & 0xff) == ((value >>> 24) & 0xff);
    }

    private String toolManifest() { return manifest("tool", "local-tool"); }

    private String skillManifest() { return manifest("skill", "local-skill"); }

    /** 追加一个能力包条目，供“包内存在该文件”的用例保持其余声明齐全。 */
    private String toolManifestWithExtraItem(String extra) {
        return toolManifest().replace("{\"name\":\"manual.md\",\"detail\":\"manual\"},",
            "{\"name\":\"manual.md\",\"detail\":\"manual\"},{\"name\":\"" + extra + "\",\"detail\":\"extra\"},");
    }

    private String manifest(String kind, String slug) {
        return "{\"schemaVersion\":\"2.1\",\"kind\":\"" + kind + "\","
            + "\"identity\":{\"slug\":\"" + slug + "\",\"name\":\"Package validation sample\",\"tagline\":\"static check\",\"description\":\"static package validation\",\"version\":\"1.0.0\",\"maintainer\":\"tester\",\"updatedAt\":\"2026-09-15\"},"
            + "\"classification\":{\"stageIds\":[],\"tags\":[],\"audiences\":[]},"
            + "\"usage\":{\"scenarios\":[\"check\"],\"inputs\":[{\"name\":\"in\",\"detail\":\"material\"}],\"outputs\":[{\"name\":\"out\",\"detail\":\"result\"}],\"workflow\":[{\"title\":\"step\",\"detail\":\"manual\"}],\"quickStart\":[\"prepare\"]},"
            + "\"quality\":{\"dimensions\":[{\"name\":\"accuracy\",\"criteria\":\"consistent\",\"evidence\":\"source\"}],\"humanReview\":[\"confirm\"],\"boundaries\":[\"test data only\"]},"
            + "\"delivery\":" + delivery(kind) + ","
            + "\"governance\":{\"status\":\"DRAFT\",\"changeNote\":\"package validation sample\"},"
            + "\"references\":{\"manual\":\"manual.md\",\"faq\":\"https://example.invalid/manual\"}}";
    }

    private String delivery(String kind) {
        String online = "{\"enabled\":false,\"acceptedExtensions\":[],\"externalProviders\":[],\"maxFileSizeMB\":20,\"processingLocation\":\"local\",\"retentionPolicy\":\"none\",\"dataNotice\":\"static sample\"}";
        String pack = "{\"environment\":\"local\",\"installGuide\":\"follow manual\",\"packageItems\":["
            + "{\"name\":\"manual.md\",\"detail\":\"manual\"},{\"name\":\"references/quality-rules.md\",\"detail\":\"rules\"}]}";
        // 工具必须登记在线处理与数据边界；这里用混合交付，保证能力包关联校验同样被执行。
        return "tool".equals(kind)
            ? "{\"mode\":\"hybrid\",\"package\":" + pack + ",\"online\":" + online + "}"
            : "{\"mode\":\"package\",\"package\":" + pack + "}";
    }
}
