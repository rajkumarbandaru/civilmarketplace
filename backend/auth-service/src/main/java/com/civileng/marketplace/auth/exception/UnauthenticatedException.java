package com.civileng.marketplace.auth.exception;

/** No valid access token. A 401, so the client refreshes its session and retries. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
