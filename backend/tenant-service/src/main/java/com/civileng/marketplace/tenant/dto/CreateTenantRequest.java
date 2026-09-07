package com.civileng.marketplace.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotBlank
    @Size(min = 2, max = 31)
    private String tenantKey;

    @NotBlank
    @Size(max = 150)
    private String name;

    /** Defaults to the tenant key when omitted — the common case is one matching the other. */
    @Size(max = 63)
    private String subdomain;

    @NotBlank
    @Email
    @Size(max = 150)
    private String contactEmail;

    @Size(max = 30)
    private String plan;

    /** Which product this tenant runs. Defaults to {@code CIVIL_MARKETPLACE}. */
    private com.civileng.marketplace.tenant.model.Vertical vertical;

    /**
     * Explicit module keys. Left empty — the usual case — the vertical's default set is used.
     */
    private java.util.Set<String> modules;

    /**
     * Logo, colours and UI styling for the tenant's console. Optional: omitted, the tenant starts
     * on the shipped platform theme and can set its own later.
     */
    private com.civileng.marketplace.tenant.common.TenantBranding branding;

    /**
     * Per-tenant navigation shaping on top of what the modules already decide: items hidden,
     * renamed, or moved. Optional — a tenant with none gets the catalogue its modules imply.
     *
     * <p>Removing a module is still the right way to take a whole area away, because it closes the
     * API route as well as the menu entry. These overrides only reshape what remains.
     */
    private java.util.List<com.civileng.marketplace.tenant.common.TenantMenuOverride> menuOverrides;

    /** Where the tenant's console opens. Null starts them on the shipped dashboard. */
    @Size(max = 200)
    private String landingPath;

    /**
     * A domain the customer owns. Optional; without one they are served at their subdomain.
     */
    @Size(max = 253)
    @jakarta.validation.constraints.Pattern(
            regexp = "^$|^(?!-)[a-zA-Z0-9-]{1,63}(\\.(?!-)[a-zA-Z0-9-]{1,63})+$",
            message = "must be a hostname like console.acme.com, with no scheme or path")
    private String customDomain;
}
