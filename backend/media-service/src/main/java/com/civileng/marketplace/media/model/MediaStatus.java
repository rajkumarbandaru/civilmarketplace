package com.civileng.marketplace.media.model;

public enum MediaStatus {
    /** A slot was issued; the browser may or may not have uploaded into it yet. */
    PENDING,
    /** Uploaded and verified. The only state whose URL is ever handed out. */
    READY,
    /** Uploaded, but the bytes did not match what was declared. The object is deleted. */
    REJECTED,
    DELETED
}
