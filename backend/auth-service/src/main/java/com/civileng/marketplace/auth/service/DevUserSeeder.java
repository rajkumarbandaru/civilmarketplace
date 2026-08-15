package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.entity.Role;
import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.repository.RoleRepository;
import com.civileng.marketplace.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Seeds one dummy account per key role for local/dev use only.
 *
 * <p>Guarded by {@code @Profile({"local","docker"})} so it can never run in any other
 * environment. Every account shares the password {@value #DEFAULT_PASSWORD}, except the
 * SUPER_ADMIN, which has its own real address and password (see {@link #SUPER_ADMIN_EMAIL}).
 */
@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
@Slf4j
public class DevUserSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "Password123!";

    /**
     * The SUPER_ADMIN uses a real, reachable address and mobile number so all three
     * notification channels can be tested end to end against a live account.
     */
    public static final String SUPER_ADMIN_EMAIL = "rajkumarbandaruit@gmail.com";
    public static final String SUPER_ADMIN_PASSWORD = "Testing@123";
    public static final String SUPER_ADMIN_PHONE = "+919493564235";
    /** The address the SUPER_ADMIN was seeded with before; migrated on startup, see {@link #reconcileSuperAdmin}. */
    private static final String LEGACY_SUPER_ADMIN_EMAIL = "superadmin@civileng.test";

    /**
     * role name -> {email, display name, phone}
     *
     * <p>Numbers are stored in E.164, matching what real registrations save: the frontend
     * converts to E.164 before submitting, so bare national numbers here would make the
     * seeded accounts unusable for phone OTP through the UI — the lookup would miss.
     */
    private static final List<String[]> DUMMY_ACCOUNTS = List.of(
            new String[]{"SUPER_ADMIN", SUPER_ADMIN_EMAIL, "Super Admin", SUPER_ADMIN_PHONE},
            new String[]{"ADMIN", "admin@civileng.test", "Platform Admin", "+919000000002"},
            new String[]{"CUSTOMER", "customer@civileng.test", "Ravi Customer", "+919000000003"},
            new String[]{"WORKER", "worker@civileng.test", "Suresh Worker", "+919000000004"},
            new String[]{"LABOUR", "labour@civileng.test", "Mahesh Labour", "+919000000005"},
            new String[]{"LABOUR_CONTRACTOR", "contractor@civileng.test", "Kiran Contractor", "+919000000006"},
            new String[]{"CIVIL_ENGINEER", "engineer@civileng.test", "Anita Engineer", "+919000000007"},
            new String[]{"ARCHITECT", "architect@civileng.test", "Priya Architect", "+919000000008"},
            new String[]{"SURVEYOR", "surveyor@civileng.test", "Vikram Surveyor", "+919000000009"},
            new String[]{"MATERIAL_SUPPLIER", "supplier@civileng.test", "Deepak Supplier", "+919000000010"}
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Role> rolesByName = roleRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getName, r -> r));

        reconcileSuperAdmin();

        int created = 0;
        for (String[] account : DUMMY_ACCOUNTS) {
            String roleName = account[0];
            String email = account[1];

            if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
                continue;
            }
            Role role = rolesByName.get(roleName);
            if (role == null) {
                log.warn("Dev seeder: role {} not found, skipping {}", roleName, email);
                continue;
            }

            userRepository.save(User.builder()
                    .email(email)
                    .phone(account[3])
                    .name(account[2])
                    .passwordHash(passwordEncoder.encode(passwordFor(email)))
                    .role(role)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());
            created++;
        }

        if (created > 0) {
            log.info("Dev seeder: created {} dummy accounts (password: {})",
                    created, DEFAULT_PASSWORD);
        } else {
            log.info("Dev seeder: all {} dummy accounts already present",
                    DUMMY_ACCOUNTS.size());
        }
    }

    private static String passwordFor(String email) {
        return SUPER_ADMIN_EMAIL.equals(email) ? SUPER_ADMIN_PASSWORD : DEFAULT_PASSWORD;
    }

    /**
     * Moves an already-seeded SUPER_ADMIN onto the current address and password.
     *
     * <p>Without this, an environment seeded before the change keeps the old account: the
     * main loop only ever creates missing accounts, so the new address would be added as a
     * second SUPER_ADMIN alongside a stale one that still accepts the old password.
     */
    private void reconcileSuperAdmin() {
        userRepository.findByEmailAndIsDeletedFalse(LEGACY_SUPER_ADMIN_EMAIL).ifPresent(user -> {
            if (userRepository.existsByEmailAndIsDeletedFalse(SUPER_ADMIN_EMAIL)) {
                // Both exist — the current one is authoritative; nothing to migrate onto it.
                log.info("Dev seeder: legacy super admin {} left as-is, {} already exists",
                        LEGACY_SUPER_ADMIN_EMAIL, SUPER_ADMIN_EMAIL);
                return;
            }
            user.setEmail(SUPER_ADMIN_EMAIL);
            user.setPasswordHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Dev seeder: super admin migrated from {} to {}",
                    LEGACY_SUPER_ADMIN_EMAIL, SUPER_ADMIN_EMAIL);
        });

        // Keeps the password and mobile number correct if the account exists but predates
        // the current values.
        userRepository.findByEmailAndIsDeletedFalse(SUPER_ADMIN_EMAIL).ifPresent(user -> {
            boolean changed = false;
            if (!passwordEncoder.matches(SUPER_ADMIN_PASSWORD, user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
                changed = true;
                log.info("Dev seeder: super admin password reset to the configured dev value");
            }
            if (!SUPER_ADMIN_PHONE.equals(user.getPhone())
                    && !userRepository.existsByPhoneAndIsDeletedFalse(SUPER_ADMIN_PHONE)) {
                user.setPhone(SUPER_ADMIN_PHONE);
                user.setPhoneVerified(true);
                changed = true;
                log.info("Dev seeder: super admin mobile number updated");
            }
            if (changed) {
                userRepository.save(user);
            }
        });

        normaliseSeededPhonesToE164();
    }

    /**
     * Rewrites the other dummy accounts' bare national numbers to E.164.
     *
     * <p>They were seeded bare, but the frontend submits E.164, so phone OTP for a seeded
     * account failed lookup with "Mobile number not registered". Only ever touches the
     * generated 90000000xx numbers, so a hand-edited number is left alone.
     */
    private void normaliseSeededPhonesToE164() {
        int updated = 0;
        for (String[] account : DUMMY_ACCOUNTS) {
            String e164 = account[3];
            if (!e164.startsWith("+91")) {
                continue;
            }
            String bare = e164.substring(3);
            if (userRepository.existsByPhoneAndIsDeletedFalse(e164)) {
                continue;
            }
            var match = userRepository.findByPhoneAndIsDeletedFalse(bare);
            if (match.isPresent()) {
                match.get().setPhone(e164);
                userRepository.save(match.get());
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Dev seeder: normalised {} dummy account phone numbers to E.164", updated);
        }
    }
}
