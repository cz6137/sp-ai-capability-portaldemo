package com.spai.portal.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.common.BusinessException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the generated copy of the repository's canonical JSON Schema. */
final class CapabilityManifestSchemaValidator {
    private final JsonNode schema;

    CapabilityManifestSchemaValidator(ObjectMapper json) {
        try (InputStream input = CapabilityManifestSchemaValidator.class.getResourceAsStream("/schema/capability-manifest.schema.json")) {
            if (input == null) throw new IllegalStateException("能力清单 Schema 未进入后端构建产物");
            schema = json.readTree(input);
        } catch (Exception error) { throw new IllegalStateException("能力清单 Schema 无法读取", error); }
    }

    void validate(JsonNode value) {
        List<String> errors = validateNode(value, schema, schema, "$");
        if (!errors.isEmpty()) throw BusinessException.badRequest("能力清单不符合 Schema：" + String.join("；", errors.subList(0, Math.min(errors.size(), 5))));
    }

    private List<String> validateNode(JsonNode value, JsonNode rule, JsonNode root, String path) {
        List<String> errors = new ArrayList<String>();
        if (rule.has("$ref")) {
            JsonNode target = resolve(root, rule.path("$ref").asText());
            if (target == null) errors.add(path + "：Schema 引用不存在"); else errors.addAll(validateNode(value, target, root, path));
            return errors;
        }
        if (rule.has("type") && !matchesType(value, rule.path("type").asText())) { errors.add(path + "：类型不符"); return errors; }
        if (rule.path("enum").isArray() && !contains(rule.path("enum"), value)) errors.add(path + "：值不在允许范围内");
        if (rule.has("const") && !rule.path("const").equals(value)) errors.add(path + "：值不符合固定要求");
        if (value.isTextual()) {
            String text = value.asText();
            if (rule.has("minLength") && text.length() < rule.path("minLength").asInt()) errors.add(path + "：文字长度不足");
            if (rule.has("pattern") && !Pattern.compile(rule.path("pattern").asText()).matcher(text).find()) errors.add(path + "：格式不符");
            if ("date".equals(rule.path("format").asText())) try { LocalDate.parse(text); } catch (Exception error) { errors.add(path + "：日期应为 YYYY-MM-DD"); }
        }
        if (value.isNumber()) {
            if (rule.has("minimum") && value.asDouble() < rule.path("minimum").asDouble()) errors.add(path + "：低于最小值");
            if (rule.has("maximum") && value.asDouble() > rule.path("maximum").asDouble()) errors.add(path + "：超过最大值");
        }
        if (value.isArray()) {
            if (rule.has("minItems") && value.size() < rule.path("minItems").asInt()) errors.add(path + "：列表项不足");
            if (rule.path("uniqueItems").asBoolean(false)) { Set<JsonNode> unique = new HashSet<JsonNode>(); for (JsonNode item : value) if (!unique.add(item)) { errors.add(path + "：列表包含重复项"); break; } }
            if (rule.path("items").isObject()) for (int index = 0; index < value.size(); index++) errors.addAll(validateNode(value.get(index), rule.path("items"), root, path + "/" + index));
        }
        if (value.isObject()) {
            JsonNode properties = rule.path("properties");
            if (rule.path("required").isArray()) for (JsonNode name : rule.path("required")) if (!value.has(name.asText())) errors.add(path + "/" + name.asText() + "：缺少必填字段");
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next(); JsonNode childRule = properties.path(field.getKey());
                if (childRule.isObject()) errors.addAll(validateNode(field.getValue(), childRule, root, path + "/" + field.getKey()));
                else if (rule.path("additionalProperties").isBoolean() && !rule.path("additionalProperties").asBoolean()) errors.add(path + "/" + field.getKey() + "：未知字段");
                else if (rule.path("additionalProperties").isObject()) errors.addAll(validateNode(field.getValue(), rule.path("additionalProperties"), root, path + "/" + field.getKey()));
            }
        }
        if (rule.path("allOf").isArray()) for (JsonNode branch : rule.path("allOf")) errors.addAll(validateNode(value, branch, root, path));
        if (rule.path("if").isObject() && validateNode(value, rule.path("if"), root, path).isEmpty() && rule.path("then").isObject()) errors.addAll(validateNode(value, rule.path("then"), root, path));
        return errors;
    }

    private static JsonNode resolve(JsonNode root, String ref) { if (!ref.startsWith("#/")) return null; JsonNode current = root; for (String token : ref.substring(2).split("/")) { current = current.path(token.replace("~1", "/").replace("~0", "~")); if (current.isMissingNode()) return null; } return current; }
    private static boolean contains(JsonNode values, JsonNode value) { for (JsonNode item : values) if (item.equals(value)) return true; return false; }
    private static boolean matchesType(JsonNode value, String type) { if ("object".equals(type)) return value.isObject(); if ("array".equals(type)) return value.isArray(); if ("string".equals(type)) return value.isTextual(); if ("integer".equals(type)) return value.isIntegralNumber(); if ("number".equals(type)) return value.isNumber(); if ("boolean".equals(type)) return value.isBoolean(); if ("null".equals(type)) return value.isNull(); return false; }
}
