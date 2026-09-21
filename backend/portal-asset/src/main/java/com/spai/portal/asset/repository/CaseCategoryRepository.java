package com.spai.portal.asset.repository;

import com.spai.portal.asset.domain.CaseCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCategoryRepository extends JpaRepository<CaseCategory, String> {
    List<CaseCategory> findByEnabledTrueOrderBySortOrderAsc();
    Optional<CaseCategory> findByCode(String code);
    Optional<CaseCategory> findByName(String name);
    Optional<CaseCategory> findByExternalFolderId(String externalFolderId);
}
