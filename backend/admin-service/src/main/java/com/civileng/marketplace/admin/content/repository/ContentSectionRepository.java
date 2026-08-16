package com.civileng.marketplace.admin.content.repository;

import com.civileng.marketplace.admin.content.model.ContentSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentSectionRepository extends JpaRepository<ContentSection, Long> {

    List<ContentSection> findAllByOrderByPageKeyAscColumnIndexAscSortOrderAscIdAsc();

    Optional<ContentSection> findBySectionKey(String sectionKey);

    boolean existsBySectionKey(String sectionKey);
}
