package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.model.Capability;

import java.util.List;
import java.util.Set;

public final class MigrationDtos {

    private MigrationDtos() {
    }

    /** One account and what the migration does, or would do, with it. */
    public record MigrationEntry(Long userId, String email, String name, String role, Set<Capability> capabilities,
                                 String outcome, Long organizationId, int catalogueItems) { }

    /** {@code dryRun}: nothing was written; the entries say what would happen. */
    public record MigrationReport(boolean dryRun, int accounts, int created, int alreadyMigrated,
                                  List<MigrationEntry> entries) { }
}
