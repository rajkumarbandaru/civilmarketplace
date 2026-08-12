package com.civileng.marketplace.notification.repository;

import com.civileng.marketplace.notification.model.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
