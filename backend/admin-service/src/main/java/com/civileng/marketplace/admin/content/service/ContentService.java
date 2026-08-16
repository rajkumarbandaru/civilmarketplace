package com.civileng.marketplace.admin.content.service;

import com.civileng.marketplace.admin.content.dto.ContentDTO.*;
import com.civileng.marketplace.admin.content.model.ContentItem;
import com.civileng.marketplace.admin.content.model.ContentSection;
import com.civileng.marketplace.admin.content.model.MediaAsset;
import com.civileng.marketplace.admin.content.repository.ContentItemRepository;
import com.civileng.marketplace.admin.content.repository.ContentSectionRepository;
import com.civileng.marketplace.admin.content.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The editable copy of the public site: every heading, paragraph, link and image on the landing
 * page and in the footer, plus the platform logo.
 *
 * <p>Reads are served to signed-out visitors, so {@link #siteContent()} filters to the enabled
 * rows and never exposes the admin-only flags beyond what the page itself prints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    /** What an admin may upload as a section image or logo. */
    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml");

    /** 2 MB. Big enough for a logo or a section illustration, small enough to hold in a row. */
    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    private final ContentSectionRepository sections;
    private final ContentItemRepository items;
    private final MediaAssetRepository media;

    // ------------------------------------------------------------------------------ public read

    /**
     * What a visitor's browser renders. Disabled sections and items are dropped here rather than
     * sent with a flag: hiding something in the console must not leave it in the payload for
     * anyone reading the network tab.
     */
    @Transactional(readOnly = true)
    public SiteContent siteContent() {
        List<Section> visible = sections.findAllByOrderByPageKeyAscColumnIndexAscSortOrderAscIdAsc().stream()
                .filter(ContentSection::isEnabled)
                .map(section -> toDto(section, true))
                .toList();

        LocalDateTime version = sections.findAll().stream()
                .map(ContentSection::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new SiteContent(visible, version);
    }

    // -------------------------------------------------------------------------------- admin read

    @Transactional(readOnly = true)
    public List<Section> allSections() {
        return sections.findAllByOrderByPageKeyAscColumnIndexAscSortOrderAscIdAsc().stream()
                .map(section -> toDto(section, false))
                .toList();
    }

    // ------------------------------------------------------------------------------ admin writes

    @Transactional
    public Section createSection(SectionCommand command) {
        String key = require(command.sectionKey(), "sectionKey");
        if (sections.existsBySectionKey(key)) {
            throw new IllegalStateException("A section with key '" + key + "' already exists");
        }
        ContentSection section = ContentSection.builder()
                .pageKey(require(command.pageKey(), "pageKey").toUpperCase())
                .sectionKey(key)
                .title(command.title())
                .subtitle(command.subtitle())
                .body(command.body())
                .imageUrl(command.imageUrl())
                .linkLabel(command.linkLabel())
                .linkUrl(command.linkUrl())
                .columnIndex(command.columnIndex() == null ? 0 : command.columnIndex())
                .sortOrder(command.sortOrder() == null ? nextSectionOrder(command.pageKey()) : command.sortOrder())
                .enabled(command.enabled() == null || command.enabled())
                // Admin-created sections carry no renderer fallback, so deleting one is a real
                // removal and is allowed.
                .systemOwned(false)
                .build();
        return toDto(sections.save(section), false);
    }

    @Transactional
    public Section updateSection(Long id, SectionCommand command) {
        ContentSection section = section(id);
        // sectionKey is deliberately not updatable: the renderer looks sections up by it, so a
        // rename would silently detach the section from the block it fills.
        if (command.pageKey() != null) section.setPageKey(command.pageKey().toUpperCase());
        if (command.title() != null) section.setTitle(blankToNull(command.title()));
        if (command.subtitle() != null) section.setSubtitle(blankToNull(command.subtitle()));
        if (command.body() != null) section.setBody(blankToNull(command.body()));
        if (command.imageUrl() != null) section.setImageUrl(blankToNull(command.imageUrl()));
        if (command.linkLabel() != null) section.setLinkLabel(blankToNull(command.linkLabel()));
        if (command.linkUrl() != null) section.setLinkUrl(blankToNull(command.linkUrl()));
        if (command.columnIndex() != null) section.setColumnIndex(command.columnIndex());
        if (command.sortOrder() != null) section.setSortOrder(command.sortOrder());
        if (command.enabled() != null) section.setEnabled(command.enabled());
        return toDto(sections.save(section), false);
    }

    @Transactional
    public void deleteSection(Long id) {
        ContentSection section = section(id);
        if (section.isSystemOwned()) {
            throw new IllegalStateException(
                    "'" + section.getSectionKey() + "' is a built-in section — hide it instead of deleting it");
        }
        sections.delete(section);
    }

    @Transactional
    public Item addItem(Long sectionId, ItemCommand command) {
        ContentSection section = section(sectionId);
        int order = command.sortOrder() != null
                ? command.sortOrder()
                : section.getItems().stream().mapToInt(ContentItem::getSortOrder).max().orElse(0) + 10;
        ContentItem item = ContentItem.builder()
                .section(section)
                .title(blankToNull(command.title()))
                .subtitle(blankToNull(command.subtitle()))
                .body(blankToNull(command.body()))
                .icon(blankToNull(command.icon()))
                .imageUrl(blankToNull(command.imageUrl()))
                .linkUrl(blankToNull(command.linkUrl()))
                .badge(blankToNull(command.badge()))
                .sortOrder(order)
                .enabled(command.enabled() == null || command.enabled())
                .build();
        return toDto(items.save(item));
    }

    @Transactional
    public Item updateItem(Long itemId, ItemCommand command) {
        ContentItem item = items.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("No content item #" + itemId));
        if (command.title() != null) item.setTitle(blankToNull(command.title()));
        if (command.subtitle() != null) item.setSubtitle(blankToNull(command.subtitle()));
        if (command.body() != null) item.setBody(blankToNull(command.body()));
        if (command.icon() != null) item.setIcon(blankToNull(command.icon()));
        if (command.imageUrl() != null) item.setImageUrl(blankToNull(command.imageUrl()));
        if (command.linkUrl() != null) item.setLinkUrl(blankToNull(command.linkUrl()));
        if (command.badge() != null) item.setBadge(blankToNull(command.badge()));
        if (command.sortOrder() != null) item.setSortOrder(command.sortOrder());
        if (command.enabled() != null) item.setEnabled(command.enabled());
        return toDto(items.save(item));
    }

    @Transactional
    public void deleteItem(Long itemId) {
        if (!items.existsById(itemId)) {
            throw new IllegalArgumentException("No content item #" + itemId);
        }
        items.deleteById(itemId);
    }

    /**
     * Re-numbers a section's items to the order the console dragged them into. Ids that do not
     * belong to the section are ignored rather than rejected, so a stale tab cannot renumber
     * somebody else's section.
     */
    @Transactional
    public Section reorderItems(Long sectionId, List<Long> orderedIds) {
        ContentSection section = section(sectionId);
        int position = 10;
        for (Long id : orderedIds) {
            for (ContentItem item : section.getItems()) {
                if (item.getId().equals(id)) {
                    item.setSortOrder(position);
                    position += 10;
                }
            }
        }
        sections.save(section);
        return toDto(section, false);
    }

    // ------------------------------------------------------------------------------------ media

    @Transactional
    public Media upload(MultipartFile file, Long adminId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        String type = file.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported image type '" + type + "'. Allowed: PNG, JPEG, GIF, WebP, SVG");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Image is larger than the 2 MB limit");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }
        MediaAsset saved = media.save(MediaAsset.builder()
                .filename(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
                .contentType(type)
                .sizeBytes(bytes.length)
                .data(bytes)
                .uploadedBy(adminId)
                .build());
        log.info("Site content image #{} uploaded by admin #{} ({} bytes)", saved.getId(), adminId, bytes.length);
        return new Media(saved.getId(), saved.getFilename(), saved.getContentType(),
                saved.getSizeBytes(), mediaUrl(saved.getId()), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<Media> listMedia() {
        return media.listSummaries().stream()
                .map(m -> new Media(m.getId(), m.getFilename(), m.getContentType(),
                        m.getSizeBytes(), mediaUrl(m.getId()), m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaAsset mediaBytes(Long id) {
        return media.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No image #" + id));
    }

    @Transactional
    public void deleteMedia(Long id) {
        if (!media.existsById(id)) {
            throw new IllegalArgumentException("No image #" + id);
        }
        media.deleteById(id);
    }

    /**
     * The path the browser fetches an upload from. Relative, so it works unchanged behind the
     * gateway, in local dev, and under whatever host the platform is deployed on.
     */
    public static String mediaUrl(Long id) {
        return "/api/v1/content/media/" + id;
    }

    // ----------------------------------------------------------------------------------- helpers

    private ContentSection section(Long id) {
        return sections.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No content section #" + id));
    }

    private int nextSectionOrder(String pageKey) {
        return sections.findAllByOrderByPageKeyAscColumnIndexAscSortOrderAscIdAsc().stream()
                .filter(s -> s.getPageKey().equalsIgnoreCase(pageKey))
                .mapToInt(ContentSection::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    private static Section toDto(ContentSection section, boolean enabledItemsOnly) {
        List<Item> rows = section.getItems().stream()
                .filter(item -> !enabledItemsOnly || item.isEnabled())
                .sorted(Comparator.comparingInt(ContentItem::getSortOrder)
                        .thenComparing(ContentItem::getId))
                .map(ContentService::toDto)
                .toList();
        return new Section(
                section.getId(), section.getPageKey(), section.getSectionKey(), section.getTitle(),
                section.getSubtitle(), section.getBody(), section.getImageUrl(), section.getLinkLabel(),
                section.getLinkUrl(), section.getColumnIndex(), section.getSortOrder(),
                section.isEnabled(), section.isSystemOwned(), rows);
    }

    private static Item toDto(ContentItem item) {
        return new Item(item.getId(), item.getTitle(), item.getSubtitle(), item.getBody(),
                item.getIcon(), item.getImageUrl(), item.getLinkUrl(), item.getBadge(),
                item.getSortOrder(), item.isEnabled());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** An emptied field means "no value", not an empty string the renderer would print as a gap. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
