package com.civileng.marketplace.admin.content.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The wire shape of the editable site content.
 *
 * <p>Reads and writes use the same record per level: the console edits a whole section or a whole
 * item and PUTs it back, so a partial-update shape would only add a way for two fields to disagree.
 */
public final class ContentDTO {

    private ContentDTO() {
    }

    public record Item(
            Long id,
            String title,
            String subtitle,
            String body,
            String icon,
            String imageUrl,
            String linkUrl,
            String badge,
            int sortOrder,
            boolean enabled) {
    }

    public record Section(
            Long id,
            String pageKey,
            String sectionKey,
            String title,
            String subtitle,
            String body,
            String imageUrl,
            String linkLabel,
            String linkUrl,
            int columnIndex,
            int sortOrder,
            boolean enabled,
            /** Seeded sections can be hidden and edited, but not deleted. */
            boolean systemOwned,
            List<Item> items) {
    }

    /** What a section save may change — never {@code sectionKey}, which the renderer looks up by. */
    public record SectionCommand(
            String pageKey,
            String sectionKey,
            String title,
            String subtitle,
            String body,
            String imageUrl,
            String linkLabel,
            String linkUrl,
            Integer columnIndex,
            Integer sortOrder,
            Boolean enabled) {
    }

    public record ItemCommand(
            String title,
            String subtitle,
            String body,
            String icon,
            String imageUrl,
            String linkUrl,
            String badge,
            Integer sortOrder,
            Boolean enabled) {
    }

    /** New order for a section's items, most-significant first. */
    public record ReorderCommand(List<Long> ids) {
    }

    public record Media(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            /** Where the image is served from — what goes into an {@code imageUrl} field. */
            String url,
            LocalDateTime createdAt) {
    }

    /**
     * Everything the public pages need, in one call.
     *
     * <p>{@code version} is the newest {@code updatedAt} across the set, so a client can tell a
     * changed payload from an unchanged one without diffing it.
     */
    public record SiteContent(List<Section> sections, LocalDateTime version) {
    }
}
