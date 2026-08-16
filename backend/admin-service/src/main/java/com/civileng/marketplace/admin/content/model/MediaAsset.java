package com.civileng.marketplace.admin.content.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An image an admin uploaded for the site — a section illustration or the platform logo.
 *
 * <p>Bytes live in the database rather than on disk because the services run without a shared
 * volume: a file written by one replica would 404 from the next.
 */
@Entity
@Table(name = "site_content_media")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // columnDefinition rather than @Lob: Hibernate maps a bare @Lob byte[] to TINYBLOB on MySQL,
    // which schema validation then rejects against the migration's LONGBLOB — and 64 KB would not
    // hold a logo anyway.
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
