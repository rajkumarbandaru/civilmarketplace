package com.civileng.marketplace.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Changing a tenant's identity: who they are, where they are served, who we contact.
 *
 * <p>There was no such request before this — the operator API could create a tenant, suspend it,
 * and change its modules, menu and branding, but nothing could correct a typo in the name or move a
 * customer onto their own domain. {@code custom_domain} in particular had existed as a column with a
 * unique index since V1 with no code path that could ever set it.
 *
 * <p>{@code tenantKey} is absent on purpose and cannot be changed here. It names this tenant's
 * schema in every service database; renaming it would mean renaming eleven schemas in lockstep,
 * which is a migration, not a form field.
 */
@Data
public class UpdateTenantRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    /**
     * The subdomain this tenant is served at. Changeable — unlike the key — because it is only a
     * routing label the gateway looks up, and a customer rebranding is a normal thing to absorb.
     */
    @NotBlank
    @Size(max = 63)
    private String subdomain;

    /**
     * A domain the customer owns, serving their console instead of the subdomain.
     *
     * <p>Blank clears it. Validated as a hostname rather than a URL: a scheme or a path here would
     * never match the {@code Host} header the gateway resolves against, so it would save cleanly
     * and then route nothing — the failure mode this pattern exists to prevent.
     */
    @Size(max = 253)
    @Pattern(regexp = "^$|^(?!-)[a-zA-Z0-9-]{1,63}(\\.(?!-)[a-zA-Z0-9-]{1,63})+$",
            message = "must be a hostname like console.acme.com, with no scheme or path")
    private String customDomain;

    @NotBlank
    @Email
    @Size(max = 150)
    private String contactEmail;

    @Size(max = 30)
    private String plan;

    /**
     * The tenant's vertical. Changing it here relabels the tenant only — it does not touch the
     * module set, which the operator edits through {@code PUT /modules} and can see the
     * consequences of. A vertical switch that silently reset modules would remove screens a live
     * tenant was using, from a field that reads like a category.
     */
    private com.civileng.marketplace.tenant.model.Vertical vertical;
}
