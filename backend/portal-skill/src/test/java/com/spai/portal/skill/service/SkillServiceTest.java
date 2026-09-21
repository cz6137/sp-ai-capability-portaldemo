package com.spai.portal.skill.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.skill.domain.Skill;
import com.spai.portal.skill.domain.SkillVersion;
import com.spai.portal.skill.repository.SkillRepository;
import com.spai.portal.skill.repository.SkillVersionRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillServiceTest {
    private SkillRepository skills;
    private SkillVersionRepository versions;
    private FileStorageService files;
    private SkillService service;

    @BeforeEach
    void setUp() {
        skills = mock(SkillRepository.class);
        versions = mock(SkillVersionRepository.class);
        files = mock(FileStorageService.class);
        service = new SkillService(skills, versions, files);
    }

    @Test
    void filtersPublishedSkillsByStageAndKeyword() {
        Skill weekly = skill("1", "项目周报生成器", 0, 20);
        Skill testing = skill("2", "测试用例生成", 6, 10);
        when(skills.findByStatusAndEnabledTrueOrderByDownloadCountDesc("PUBLISHED")).thenReturn(Arrays.asList(weekly, testing));

        List<Skill> result = service.published(0, "周报");

        assertEquals(1, result.size());
        assertSame(weekly, result.get(0));
    }

    @Test
    void downloadIncrementsCounterAndLoadsPublishedPackage() {
        Skill weekly = skill("1", "项目周报生成器", 0, 20);
        SkillVersion version = new SkillVersion();
        version.setId("v1"); version.setSkillId("1"); version.setStatus("PUBLISHED"); version.setFileId("file-1");
        when(skills.findById("1")).thenReturn(Optional.of(weekly));
        when(versions.findFirstBySkillIdAndStatusOrderByCreatedAtDesc("1", "PUBLISHED")).thenReturn(Optional.of(version));

        service.download("1");

        assertEquals(21, weekly.getDownloadCount());
        verify(skills).save(weekly);
        verify(files).load("file-1");
    }

    @Test
    void publishingVersionPublishesSkill() {
        Skill weekly = skill("1", "项目周报生成器", 0, 20);
        weekly.setStatus("DRAFT");
        SkillVersion version = new SkillVersion();
        version.setId("v1"); version.setSkillId("1"); version.setStatus("APPROVED");
        when(versions.findById("v1")).thenReturn(Optional.of(version));
        when(skills.findById("1")).thenReturn(Optional.of(weekly));

        service.transition("v1", "publish", "通过");

        assertEquals("PUBLISHED", version.getStatus());
        assertEquals("PUBLISHED", weekly.getStatus());
        verify(versions).save(version);
        verify(skills).save(weekly);
    }

    private Skill skill(String id, String name, int stage, long downloads) {
        Skill skill = new Skill();
        skill.setId(id); skill.setName(name); skill.setSlug("skill-" + id); skill.setDescription(name);
        skill.setStatus("PUBLISHED"); skill.setEnabled(true); skill.setDownloadCount(downloads);
        skill.setStageIds(new LinkedHashSet<Integer>(Arrays.asList(stage)));
        return skill;
    }
}
