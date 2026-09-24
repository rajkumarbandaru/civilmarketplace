package com.civileng.marketplace.gateway.tenant;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TenantDirectoryTtlTest {

    private static TenantDescriptor withStatus(String status) {
        TenantDescriptor d = new TenantDescriptor();
        d.setStatus(status);
        return d;
    }

    @Test
    void aLiveTenantIsCachedForAtMostThirtySeconds() {
        assertThat(TenantDirectory.ttlFor(withStatus("ACTIVE"))).isEqualTo(Duration.ofSeconds(30));
    }

    /** A just-published workspace's owner must not be told "draft" for a minute after it went live. */
    @Test
    void aTenantThatIsNotLiveIsRecheckedWithinSeconds() {
        for (String status : new String[]{"DRAFT", "PROVISIONING", "PROVISIONING_FAILED", "SUSPENDED"}) {
            assertThat(TenantDirectory.ttlFor(withStatus(status))).as(status).isLessThanOrEqualTo(Duration.ofSeconds(3));
        }
    }
}
