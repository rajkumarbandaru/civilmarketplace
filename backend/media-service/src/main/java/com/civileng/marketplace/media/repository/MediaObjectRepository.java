package com.civileng.marketplace.media.repository;

import com.civileng.marketplace.media.model.MediaObject;
import com.civileng.marketplace.media.model.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MediaObjectRepository extends JpaRepository<MediaObject, String> {

    List<MediaObject> findTop500ByStatusAndCreatedAtBefore(MediaStatus status, LocalDateTime cutoff);
}
