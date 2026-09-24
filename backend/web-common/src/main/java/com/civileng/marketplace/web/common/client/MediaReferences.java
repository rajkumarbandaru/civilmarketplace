package com.civileng.marketplace.web.common.client;

import feign.FeignException;

/**
 * The two things a service does with an uploaded file: check one before saving a reference to it,
 * and get a fresh link to one it already references.
 *
 * <p>Files are referenced by id, never by a URL the client sends: a URL is whatever the client
 * typed, while an id is checked here against what media-service actually stored — its purpose
 * (a profile photo cannot be passed off as a KYC document) and its uploader.
 */
public class MediaReferences {

    private final MediaClient client;

    public MediaReferences(MediaClient client) {
        this.client = client;
    }

    /**
     * The file, if it is READY, was uploaded for {@code purpose}, and by {@code ownerUserId}.
     *
     * @throws IllegalArgumentException otherwise — one message for all cases, so a caller cannot
     *                                  probe for other people's file ids
     */
    public MediaRef requireOwned(String mediaId, String purpose, Long ownerUserId) {
        if (mediaId == null || mediaId.isBlank() || ownerUserId == null) {
            throw new IllegalArgumentException("Upload a file first");
        }
        MediaRef ref = fetch(mediaId, ownerUserId);
        if (!purpose.equals(ref.purpose()) || !ownerUserId.equals(ref.ownerUserId())) {
            throw new IllegalArgumentException("Uploaded file not found");
        }
        return ref;
    }

    /** A current link to a file this service has already decided {@code onBehalfOf} may open. */
    public MediaRef fresh(String mediaId, Long onBehalfOf) {
        return fetch(mediaId, onBehalfOf);
    }

    private MediaRef fetch(String mediaId, Long onBehalfOf) {
        try {
            return client.get(mediaId, onBehalfOf);
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Uploaded file not found");
        } catch (FeignException e) {
            throw new MediaUnavailableException("File storage is unavailable, try again shortly", e);
        }
    }
}
