package com.civileng.marketplace.tenant.common;

/**
 * The tenant a thread is currently serving. Set by {@link TenantHeaderFilter} from the
 * gateway-injected {@code X-Tenant-Id} header and read back by Hibernate's tenant resolver
 * to pick the schema every query runs against.
 *
 * <p>Anything that leaves the request thread — {@code @Async}, a Kafka listener, a scheduled
 * job — starts with no tenant bound and must call {@link #runAs} explicitly. There is no
 * ambient fallback on purpose: a missing tenant must fail loudly rather than silently read
 * another tenant's schema.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** The bound tenant, or {@code null} when the thread is running outside any tenant. */
    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant bound to this thread — the request never passed the gateway's "
                            + "tenant filter, or this is background work that must use TenantContext.runAs()");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code work} bound to {@code tenantId}, restoring whatever was bound before.
     * Use this for Kafka listeners, schedulers and any cross-tenant admin sweep.
     */
    public static void runAs(String tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * {@link #runAs} for work that returns something — an operator reading one value out of
     * another tenant's schema, typically. Same restore-what-was-bound contract.
     */
    public static <T> T callAs(String tenantId, java.util.function.Supplier<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
