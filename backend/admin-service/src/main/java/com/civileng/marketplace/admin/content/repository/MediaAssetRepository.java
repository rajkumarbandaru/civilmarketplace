package com.civileng.marketplace.admin.content.repository;

import com.civileng.marketplace.admin.content.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** Everything about an upload except its bytes — a listing must not drag every blob into memory. */
    interface MediaSummary {
        Long getId();
        String getFilename();
        String getContentType();
        long getSizeBytes();
        LocalDateTime getCreatedAt();
    }

    @Query("SELECT m.id AS id, m.filename AS filename, m.contentType AS contentType, "
            + "m.sizeBytes AS sizeBytes, m.createdAt AS createdAt "
            + "FROM MediaAsset m ORDER BY m.id DESC")
    List<MediaSummary> listSummaries();
}
