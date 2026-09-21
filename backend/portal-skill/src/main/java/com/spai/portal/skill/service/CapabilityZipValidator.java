package com.spai.portal.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.common.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Java-8 compatible bounded ZIP inspection. */
final class CapabilityZipValidator {
    private static final long TOTAL = 500L * 1024 * 1024;
    private static final long ENTRY = 100L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = TOTAL;
    static final long MAX_ENTRY_BYTES = ENTRY;
    private final ObjectMapper json;

    CapabilityZipValidator(ObjectMapper json) { this.json = json; }

    void validate(JsonNode manifest, File file) throws IOException {
        Set<String> names = new HashSet<String>();
        long total = 0;
        byte[] capability = null;
        try (ZipFile zip = new ZipFile(file, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName().replace('\\', '/');
                require(safe(path) && names.add(path), "能力包路径非法或重复");
                require(entry.getMethod() == ZipEntry.STORED || entry.getMethod() == ZipEntry.DEFLATED, "能力包加密或压缩方式不受支持");
                if (entry.isDirectory()) continue;
                require(entry.getSize() >= 0 && entry.getSize() <= ENTRY, "能力包单文件超过限制");
                try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count; long one = 0;
                    while ((count = input.read(buffer)) > 0) {
                        one += count; total += count;
                        require(one <= ENTRY && total <= TOTAL, "能力包解压大小超过限制");
                        if ("capability.json".equals(path)) output.write(buffer, 0, count);
                    }
                    if ("capability.json".equals(path)) capability = output.toByteArray();
                }
            }
        }
        require(capability != null, "能力包缺少根目录 capability.json");
        JsonNode packaged;
        try { packaged = json.readTree(capability); } catch (Exception error) { throw BusinessException.badRequest("包内 capability.json 无效"); }
        require(packaged != null && packaged.isObject(), "包内 capability.json 必须为对象");
        require(eq(packaged, "schemaVersion", manifest, "schemaVersion") && eq(packaged, "kind", manifest, "kind")
            && eq(packaged.path("identity"), "slug", manifest.path("identity"), "slug")
            && eq(packaged.path("identity"), "version", manifest.path("identity"), "version"), "包内清单与上传清单不一致");

        for (JsonNode item : manifest.path("delivery").path("package").path("packageItems")) {
            String path = item.path("ref").asText("");
            if (path.isEmpty()) path = item.path("name").asText("");
            path = path.replace('\\', '/');
            require(declaredPathExists(names, path), "清单声明的包内文件不存在：" + path);
        }
        Iterator<String> referenceNames = manifest.path("references").fieldNames();
        while (referenceNames.hasNext()) {
            String key = referenceNames.next(); JsonNode value = manifest.path("references").path(key);
            require(value.isTextual() && !value.asText().trim().isEmpty(), "参考资料位置必须为非空文字：" + key);
            String path = value.asText().trim().replace('\\', '/');
            if (!isRemote(path)) require(names.contains(path), "清单引用的本地参考文件不存在：" + path);
        }
        if ("skill".equals(manifest.path("kind").asText())) require(names.contains("SKILL.md"), "Skill 包必须包含根目录 SKILL.md");
    }

    private static boolean declaredPathExists(Set<String> names, String path) {
        if ("./".equals(path)) return !names.isEmpty();
        if (path.endsWith("/")) for (String name : names) if (name.startsWith(path) && !name.equals(path)) return true;
        return names.contains(path);
    }
    private static boolean safe(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains("\0")) return false;
        for (String part : path.split("/")) if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        return true;
    }
    private static boolean isRemote(String path) { return path.toLowerCase(Locale.ROOT).matches("^https?://.*"); }
    private static boolean eq(JsonNode left, String leftField, JsonNode right, String rightField) { return left.path(leftField).equals(right.path(rightField)); }
    private static void require(boolean condition, String message) { if (!condition) throw BusinessException.badRequest(message); }
}
