package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.model.Capability;
import com.civileng.marketplace.procurement.model.MemberRole;
import com.civileng.marketplace.procurement.model.RelationshipType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record OrganizationRequest(
            @NotBlank @Size(max = 160) String name,
            @Pattern(regexp = "^$|^[0-9A-Z]{15}$", message = "GSTIN is 15 letters and digits") String gstin,
            @NotEmpty Set<Capability> capabilities,
            @DecimalMin("0") BigDecimal approvalThreshold) { }

    public record OrganizationView(Long id, String name, String gstin, Set<Capability> capabilities,
                                   BigDecimal approvalThreshold, BigDecimal effectiveApprovalThreshold,
                                   MemberRole myRole) { }

    public record OrganizationDetail(OrganizationView organization, List<MemberView> members,
                                     List<RelationshipView> relationships) { }

    public record MemberRequest(@NotBlank @Email String email, @NotNull MemberRole role) { }

    /** {@code joined} is false until the person has used procurement once. */
    public record MemberView(Long id, String email, MemberRole role, boolean joined) { }

    public record RelationshipRequest(@NotNull Long targetOrgId, @NotNull RelationshipType type) { }

    public record RelationshipView(Long id, Long targetOrgId, String targetOrgName, RelationshipType type) { }

    /** An organization as others see it when choosing whom to trade with. */
    public record DirectoryEntry(Long id, String name, Set<Capability> capabilities, boolean preferred,
                                 boolean contracted) { }
}
