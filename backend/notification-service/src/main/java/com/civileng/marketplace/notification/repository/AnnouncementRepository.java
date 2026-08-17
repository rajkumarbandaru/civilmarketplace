package com.civileng.marketplace.notification.repository;

import com.civileng.marketplace.notification.model.Announcement;
import com.civileng.marketplace.notification.model.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Scheduled announcements whose time has come, oldest appointment first. */
    List<Announcement> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            AnnouncementStatus status, Instant cutoff);

    /**
     * Takes ownership of one due announcement, returning 1 if this caller got it and 0 if someone
     * else already had.
     *
     * The status test lives in the WHERE clause on purpose. Two service instances run the same
     * scheduler on the same minute and will read the same due row; without a conditional claim
     * both would fan out and every recipient would get the announcement twice. Whichever UPDATE
     * reaches the row first flips it out of SCHEDULED and the other one matches nothing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Announcement a SET a.status = com.civileng.marketplace.notification.model."
            + "AnnouncementStatus.SENDING WHERE a.id = :id AND a.status = com.civileng.marketplace"
            + ".notification.model.AnnouncementStatus.SCHEDULED")
    int claimForSending(@Param("id") Long id);
}
