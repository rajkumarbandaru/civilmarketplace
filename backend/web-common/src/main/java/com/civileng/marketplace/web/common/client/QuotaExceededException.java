package com.civileng.marketplace.web.common.client;

/** A hard plan limit reached. Served as 402: the fix is commercial (upgrade, add-on, grant). */
public class QuotaExceededException extends RuntimeException {

    private final String limit;
    private final long allowed;

    public QuotaExceededException(String limit, long allowed, String message) {
        super(message);
        this.limit = limit;
        this.allowed = allowed;
    }

    public String limit() {
        return limit;
    }

    public long allowed() {
        return allowed;
    }
}
