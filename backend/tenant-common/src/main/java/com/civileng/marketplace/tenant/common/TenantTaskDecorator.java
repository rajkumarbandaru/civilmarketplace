package com.civileng.marketplace.tenant.common;

import org.springframework.core.task.TaskDecorator;

/**
 * Carries the submitting thread's tenant onto {@code @Async} work.
 *
 * <p>Without it an async method starts with no tenant bound, and Hibernate's resolver falls back to
 * the bootstrap tenant — so an email log written by tenant B's async send landed in the bootstrap
 * tenant's schema, and its credentials were looked up for nobody. Spring Boot applies a
 * {@link TaskDecorator} bean to its auto-configured executor, so registering this is enough.
 */
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String tenant = TenantContext.get();
        if (tenant == null) {
            return runnable;
        }
        return () -> TenantContext.runAs(tenant, runnable);
    }
}
