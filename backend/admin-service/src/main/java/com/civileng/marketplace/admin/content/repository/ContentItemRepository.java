package com.civileng.marketplace.admin.content.repository;

import com.civileng.marketplace.admin.content.model.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
}
