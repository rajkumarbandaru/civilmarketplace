package com.civileng.marketplace.analytics.cdc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code analytics.cdc.*}: the replication account every cluster grants, and whether to capture. */
@ConfigurationProperties(prefix = "analytics.cdc")
public record CdcProperties(boolean enabled, String username, String password, long serverIdBase) {

    public CdcProperties {
        username = username == null || username.isBlank() ? "civil_cdc" : username;
        serverIdBase = serverIdBase <= 0 ? 5400 : serverIdBase;
    }
}
