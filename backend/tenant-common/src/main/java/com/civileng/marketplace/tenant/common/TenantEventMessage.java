package com.civileng.marketplace.tenant.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** What tenant-service publishes when a tenant is created, suspended or reactivated. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEventMessage {

    private String tenantKey;
    private String name;
    private String subdomain;
    private String status;
    private Instant occurredAt;

    /**
     * The tenant's chosen look, carried so admin-service can seed its theme the moment the schema
     * exists. Null for every event but a creation — branding is applied once, at provisioning;
     * afterwards the tenant's own Theme &amp; UI style screen owns it.
     */
    private TenantBranding branding;

    /**
     * True when this event exists *because* an operator changed the branding, rather than carrying
     * it along with a creation.
     *
     * <p>The distinction decides whether a tenant's own theme may be overwritten. On creation the
     * branding is a seed and must not clobber anything; on an explicit edit the operator has been
     * shown what they are replacing and said yes, so it applies unconditionally.
     */
    private boolean brandingUpdate;

    /**
     * The tenant's enabled module keys. Carried on every event, because a service cannot ask for
     * them at request time — they live in tenant-service, and the menu has to be filtered inside
     * the tenant's own schema. Services keep their own synced copy from this.
     */
    private java.util.Set<String> modules;

    /**
     * The operator's per-tenant navigation decisions: what is hidden, what is renamed, what order
     * it sits in. On top of what the module set already decides, so a module the tenant does not
     * have takes its items with it whether or not there is a row here.
     *
     * <p>Carried in full on every event and applied wholesale by consumers. A merge would leave an
     * item the operator un-hid still hidden, because "no longer overridden" is an absence and
     * absences do not survive a merge.
     */
    private java.util.List<TenantMenuOverride> menuOverrides;

    /**
     * Where this tenant's console opens — a path from the menu catalogue, or null for the shipped
     * dashboard. Operator-set because the first screen is the one thing a customer notices before
     * they have learned the navigation, and for a tenant that bought one module the shipped
     * dashboard is usually not it.
     */
    private String landingPath;
}
