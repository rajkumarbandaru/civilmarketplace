package com.civileng.marketplace.auth.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Who the dev seeder puts on the platform console, and under which addresses. */
class DevUserSeederPlatformStaffTest {

    private static List<String> emails(List<String[]> accounts) {
        return accounts.stream().map(a -> a[1]).toList();
    }

    @Test
    void thePlatformStaffHaveTheirOwnRealAddresses() {
        assertThat(DevUserSeeder.accountsFor("platform")).extracting(a -> a[0] + " " + a[1]).containsExactly(
                "PLATFORM_OWNER bandarurajkumar239@gmail.com",
                "PLATFORM_ADMIN officialrktech.admin@gmail.com",
                "PLATFORM_SUPPORT officialrktech.support@gmail.com");
    }

    @Test
    void aTenantsOwnerKeepsTheirAddressAndSharesNoneWithThePlatform() {
        assertThat(DevUserSeeder.accountsFor("civengmarket").get(0)[1]).isEqualTo("rajkumarbandaruit@gmail.com");
        assertThat(emails(DevUserSeeder.accountsFor("civengmarket")))
                .doesNotContainAnyElementsOf(emails(DevUserSeeder.accountsFor("platform")));
    }

    @Test
    void thePlatformStaffShareThePlatformPasswordAndTenantsKeepTheirs() {
        assertThat(DevUserSeeder.PLATFORM_PASSWORD).isEqualTo("Smiley@123");
        assertThat(DevUserSeeder.passwordFor("bandarurajkumar239@gmail.com")).isEqualTo("Smiley@123");
        assertThat(DevUserSeeder.passwordFor("officialrktech.admin@gmail.com")).isEqualTo("Smiley@123");
        assertThat(DevUserSeeder.passwordFor("officialrktech.support@gmail.com")).isEqualTo("Smiley@123");
        assertThat(DevUserSeeder.passwordFor("rajkumarbandaruit@gmail.com")).isEqualTo(DevUserSeeder.OWNER_PASSWORD);
        assertThat(DevUserSeeder.passwordFor("admin@civileng.test")).isEqualTo(DevUserSeeder.DEFAULT_PASSWORD);
    }

    @Test
    void theOldPlatformAddressesMoveToTheNewOnes() {
        assertThat(DevUserSeeder.PLATFORM_RENAMES)
                .containsEntry("rajkumarbandaruit@gmail.com", "bandarurajkumar239@gmail.com")
                .containsEntry("platform-admin@rktech.test", "officialrktech.admin@gmail.com")
                .containsEntry("platform-support@rktech.test", "officialrktech.support@gmail.com");
    }
}
