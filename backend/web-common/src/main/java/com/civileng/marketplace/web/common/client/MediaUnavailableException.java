package com.civileng.marketplace.web.common.client;

/** media-service could not be reached to check a file. A 503: the caller should retry shortly. */
public class MediaUnavailableException extends IllegalStateException {

    public MediaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
