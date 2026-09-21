package com.spai.portal.skill.repository;

import com.spai.portal.skill.domain.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, String> {
    List<Skill> findByStatusAndEnabledTrueOrderByDownloadCountDesc(String status);
    List<Skill> findAllByOrderByUpdatedAtDesc();
    Optional<Skill> findBySlug(String slug);
}
