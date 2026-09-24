package com.civileng.marketplace.media.model;

import java.util.Set;

import static com.civileng.marketplace.media.model.UploaderScope.*;
import static com.civileng.marketplace.media.model.Visibility.*;

/**
 * Every reason a file can be uploaded, and the rules that come with it.
 *
 * <p>The purpose is declared up front and decides everything the caller does not get to choose:
 * which bucket, how large, which types, and who may upload. A client cannot, for instance, put a
 * KYC document in the public bucket by asking for it — there is no such option to ask for.
 *
 * <p>SVG is deliberately absent everywhere: it is a script-capable document, and one served from
 * the public bucket would run in whatever origin embedded it.
 */
public enum MediaPurpose {

    AVATAR(PUBLIC, ANY_USER, 5, Types.IMAGES),
    PORTFOLIO(PUBLIC, ANY_USER, 10, Types.IMAGES),
    SERVICE_MEDIA(PUBLIC, STAFF, 20, Types.IMAGES_AND_VIDEO),
    CATEGORY_IMAGE(PUBLIC, STAFF, 5, Types.IMAGES),
    BRAND_LOGO(PUBLIC, STAFF, 2, Types.IMAGES),
    TENANT_LOGO(PUBLIC, SUPER_ADMIN, 2, Types.IMAGES),
    KYC_DOCUMENT(PRIVATE, ANY_USER, 10, Types.IMAGES_AND_PDF),
    PROJECT_DOCUMENT(PRIVATE, ANY_USER, 20, Types.IMAGES_AND_PDF),
    SUPPORT_ATTACHMENT(PRIVATE, ANY_USER, 10, Types.IMAGES_AND_PDF),
    REVIEW_PHOTO(PUBLIC, ANY_USER, 10, Types.IMAGES);

    private final Visibility visibility;
    private final UploaderScope uploaders;
    private final long maxBytes;
    private final Set<String> contentTypes;

    MediaPurpose(Visibility visibility, UploaderScope uploaders, int maxMegabytes, Set<String> contentTypes) {
        this.visibility = visibility;
        this.uploaders = uploaders;
        this.maxBytes = maxMegabytes * 1024L * 1024L;
        this.contentTypes = contentTypes;
    }

    public Visibility visibility() {
        return visibility;
    }

    public UploaderScope uploaders() {
        return uploaders;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Set<String> contentTypes() {
        return contentTypes;
    }

    /** The path segment used in object keys: {@code KYC_DOCUMENT} → {@code kyc-document}. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    private static final class Types {
        static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
        static final Set<String> IMAGES_AND_VIDEO = Set.of(
                "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm");
        static final Set<String> IMAGES_AND_PDF = Set.of(
                "image/jpeg", "image/png", "image/webp", "application/pdf");
    }
}
