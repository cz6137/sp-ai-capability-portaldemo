package com.spai.portal.skill.repository;

import com.spai.portal.skill.domain.SkillVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, String> {
    List<SkillVersion> findBySkillIdOrderByCreatedAtDesc(String skillId);
    Optional<SkillVersion> findFirstBySkillIdAndStatusOrderByCreatedAtDesc(String skillId, String status);
    Optional<SkillVersion> findFirstBySkillIdOrderByCreatedAtDesc(String skillId);
}
