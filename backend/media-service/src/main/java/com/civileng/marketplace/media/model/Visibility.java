package com.civileng.marketplace.media.model;

/**
 * Which bucket a file lives in.
 *
 * <p>PUBLIC objects are readable by anyone holding the URL — logos, service photos, avatars — and
 * can sit behind a CDN. PRIVATE objects are never readable without a short-lived signed URL, which
 * this service issues only after checking who is asking.
 */
public enum Visibility {
    PUBLIC,
    PRIVATE
}
