package com.civileng.marketplace.audit.common;

public enum AuditAction {
    /** A read of personal/sensitive data — logged because DPDP requires access logging. */
    READ,
    CREATE,
    UPDATE,
    DELETE,
    APPROVE,
    REJECT,
    LOGIN,
    EXPORT,
    ERASURE_REQUEST
}
