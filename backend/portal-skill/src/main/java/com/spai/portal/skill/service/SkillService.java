package com.spai.portal.skill.service;

import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.common.BusinessException;
import com.spai.portal.skill.domain.Skill;
import com.spai.portal.skill.domain.SkillVersion;
import com.spai.portal.skill.repository.SkillRepository;
import com.spai.portal.skill.repository.SkillVersionRepository;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillService {
    private final SkillRepository skills;
    private final SkillVersionRepository versions;
    private final FileStorageService files;

    public SkillService(SkillRepository skills, SkillVersionRepository versions, FileStorageService files) {
        this.skills = skills;
        this.versions = versions;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<Skill> published(Integer stage, String query) {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return skills.findByStatusAndEnabledTrueOrderByDownloadCountDesc("PUBLISHED").stream()
            .filter(s -> stage == null || s.getStageIds().contains(stage))
            .filter(s -> q.isEmpty() || searchable(s).contains(q))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> managed() {
        return skills.findAllByOrderByUpdatedAtDesc().stream().map(this::summary).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        Skill skill = required(id);
        List<SkillVersion> allVersions = versions.findBySkillIdOrderByCreatedAtDesc(id);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("skill", skill);
        result.put("versions", allVersions);
        result.put("downloadAvailable", allVersions.stream().anyMatch(v -> v.getFileId() != null && "PUBLISHED".equals(v.getStatus())));
        return result;
    }

    @Transactional
    public Skill save(String id, SkillForm form, String userId) {
        Skill skill = id == null ? new Skill() : required(id);
        if (id == null) {
            skill.setId(UUID.randomUUID().toString());
            skill.setOwnerId(userId);
            skill.setStatus("DRAFT");
        }
        apply(skill, form);
        skill.setUpdatedAt(OffsetDateTime.now());
        return skills.save(skill);
    }

    @Transactional
    public Skill importPackage(MultipartFile upload, SkillForm form, String userId, boolean publish) {
        if (upload == null || upload.isEmpty()) throw new BusinessException("SKILL_FILE_REQUIRED", "请选择 Skill 包", HttpStatus.BAD_REQUEST);
        FileObject stored = files.store(upload);
        String slug = normalizeSlug(form.slug, form.name);
        form.slug = slug;
        Skill skill = form.id == null ? skills.findBySlug(slug).orElse(new Skill()) : required(form.id);
        if (skill.getId() == null) {
            skill.setId(UUID.randomUUID().toString());
            skill.setOwnerId(userId);
        }
        apply(skill, form);
        skill.setStatus(publish ? "PUBLISHED" : "DRAFT");
        skill.setUpdatedAt(OffsetDateTime.now());
        skills.save(skill);

        SkillVersion version = new SkillVersion();
        version.setId(UUID.randomUUID().toString());
        version.setSkillId(skill.getId());
        version.setVersionName(blank(form.versionName) ? nextVersion(skill.getId()) : form.versionName.trim());
        version.setFileId(stored.getId());
        version.setStatus(publish ? "PUBLISHED" : "DRAFT");
        version.setChangeNote(form.changeNote);
        version.setCreatedBy(userId);
        versions.save(version);
        return skill;
    }

    @Transactional
    public FileStorageService.StoredFile download(String id) {
        Skill skill = required(id);
        SkillVersion version = versions.findFirstBySkillIdAndStatusOrderByCreatedAtDesc(id, "PUBLISHED")
            .orElseThrow(() -> BusinessException.notFound("该 Skill 暂无可下载的已发布版本"));
        if (version.getFileId() == null) throw BusinessException.notFound("该 Skill 版本未关联文件");
        skill.setDownloadCount(skill.getDownloadCount() + 1);
        skill.setUpdatedAt(OffsetDateTime.now());
        skills.save(skill);
        return files.load(version.getFileId());
    }

    @Transactional
    public SkillVersion transition(String versionId, String action, String note) {
        SkillVersion version = versions.findById(versionId).orElseThrow(() -> BusinessException.notFound("版本不存在"));
        String next;
        if ("submit".equals(action) && "DRAFT".equals(version.getStatus())) next = "IN_REVIEW";
        else if ("approve".equals(action) && "IN_REVIEW".equals(version.getStatus())) next = "APPROVED";
        else if ("publish".equals(action) && "APPROVED".equals(version.getStatus())) next = "PUBLISHED";
        else if ("reject".equals(action) && !"PUBLISHED".equals(version.getStatus())) next = "DRAFT";
        else throw BusinessException.conflict("当前状态不允许此操作");
        version.setStatus(next);
        version.setReviewNote(note);
        versions.save(version);
        Skill skill = required(version.getSkillId());
        if ("PUBLISHED".equals(next)) skill.setStatus("PUBLISHED");
        skill.setUpdatedAt(OffsetDateTime.now());
        skills.save(skill);
        return version;
    }

    @Transactional
    public void archive(String id) {
        Skill skill = required(id);
        skill.setEnabled(false);
        skill.setStatus("ARCHIVED");
        skill.setUpdatedAt(OffsetDateTime.now());
        skills.save(skill);
    }

    private Map<String, Object> summary(Skill skill) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("skill", skill);
        value.put("latestVersion", versions.findFirstBySkillIdOrderByCreatedAtDesc(skill.getId()).orElse(null));
        return value;
    }

    private void apply(Skill skill, SkillForm form) {
        if (blank(form.name)) throw new BusinessException("SKILL_NAME_REQUIRED", "Skill 名称不能为空", HttpStatus.BAD_REQUEST);
        skill.setName(form.name.trim());
        skill.setSlug(normalizeSlug(form.slug, form.name));
        skill.setDescription(form.description);
        skill.setSourceType(blank(form.sourceType) ? "INTERNAL" : form.sourceType.toUpperCase(Locale.ROOT));
        skill.setCaseText(form.caseText);
        skill.setUsageGuide(form.usageGuide);
        Set<Integer> stageIds = new LinkedHashSet<Integer>();
        if (form.stageIds != null) stageIds.addAll(form.stageIds);
        skill.setStageIds(stageIds);
        skill.setEnabled(true);
    }

    private String nextVersion(String skillId) {
        return "1.0." + versions.findBySkillIdOrderByCreatedAtDesc(skillId).size();
    }

    private String normalizeSlug(String slug, String name) {
        String source = blank(slug) ? name : slug;
        String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return value.isEmpty() ? "skill-" + UUID.randomUUID().toString().substring(0, 8) : value;
    }

    private String searchable(Skill skill) {
        return (skill.getName() + " " + Optional.ofNullable(skill.getDescription()).orElse("") + " " + skill.getSlug()).toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private Skill required(String id) { return skills.findById(id).orElseThrow(() -> BusinessException.notFound("Skill 不存在")); }

    public static class SkillForm {
        public String id;
        public String slug;
        public String name;
        public String description;
        public String sourceType;
        public String caseText;
        public String usageGuide;
        public List<Integer> stageIds = new ArrayList<Integer>();
        public String versionName;
        public String changeNote;
    }
}
