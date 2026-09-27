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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.civileng.marketplace.tenant.common.CrossTenantRunner;

import java.util.List;
import java.util.Map;

/**
 * Seeds one dummy account per key role for local/dev use only.
 *
 * <p>Guarded by {@code @Profile({"local","docker"})} so it can never run in any other
 * environment. Every account shares the password {@value #DEFAULT_PASSWORD}, except the
 * tenant owner ({@link #OWNER_EMAIL}, {@link #OWNER_PASSWORD}) and the platform staff
 * ({@link #PLATFORM_OWNER_EMAIL} and the others, {@link #PLATFORM_PASSWORD}).
 *
 * <p>Two rosters, because the two kinds of tenant have different staff. The operator tenant
 * ({@code platform}, the platform company) gets the platform staff and nothing else — it runs the console,
 * not a business. Every customer tenant gets its own owner, admin and one account per member role.
 * The platform's staff have their own real addresses, apart from the tenant owner's, so the
 * console's accounts and a tenant's can never be mistaken for one another.
 *
 * <p>Runs once per active tenant. Emails are unique per schema rather than platform-wide, so the
 * same dummy addresses exist independently in each tenant and signing in as
 * {@code admin@civileng.test} on two different tenant hosts reaches two different accounts — which
 * is what makes the tenancy visible from a browser.
 */
@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
@Slf4j
public class DevUserSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "Password123!";

    /**
     * The owner uses a real, reachable address and mobile number so all three notification
     * channels can be tested end to end against a live account.
     */
    public static final String OWNER_EMAIL = "rajkumarbandaruit@gmail.com";
    public static final String OWNER_PASSWORD = "Testing@123";
    public static final String OWNER_PHONE = "+919493564235";
    /** The address the owner was seeded with before; migrated on startup, see {@link #reconcileOwner}. */
    private static final String LEGACY_OWNER_EMAIL = "superadmin@civileng.test";

    /** The platform company's staff: real, reachable addresses, one per platform role. */
    public static final String PLATFORM_OWNER_EMAIL = "bandarurajkumar239@gmail.com";
    public static final String PLATFORM_ADMIN_EMAIL = "officialrktech.admin@gmail.com";
    public static final String PLATFORM_SUPPORT_EMAIL = "officialrktech.support@gmail.com";
    /** Shared by the three platform staff accounts above. */
    public static final String PLATFORM_PASSWORD = "Smiley@123";

    /**
     * Addresses the platform staff were seeded with before -> the current ones. Renamed in place on
     * startup (operator tenant only) so the accounts keep their passwords and authenticators rather
     * than a second set being created beside them.
     */
    static final Map<String, String> PLATFORM_RENAMES = Map.of(
            OWNER_EMAIL, PLATFORM_OWNER_EMAIL,
            "platform-admin@rktech.test", PLATFORM_ADMIN_EMAIL,
            "platform-support@rktech.test", PLATFORM_SUPPORT_EMAIL);

    /**
     * role name -> {email, display name, phone}
     *
     * <p>Numbers are stored in E.164, matching what real registrations save: the frontend
     * converts to E.164 before submitting, so bare national numbers here would make the
     * seeded accounts unusable for phone OTP through the UI — the lookup would miss.
     */
    static final List<String[]> TENANT_ACCOUNTS = List.of(
            new String[]{"TENANT_OWNER", OWNER_EMAIL, "Tenant Owner", OWNER_PHONE},
            new String[]{"ADMIN", "admin@civileng.test", "Tenant Admin", "+919000000002"},
            new String[]{"CUSTOMER", "customer@civileng.test", "Ravi Customer", "+919000000003"},
            new String[]{"WORKER", "worker@civileng.test", "Suresh Worker", "+919000000004"},
            new String[]{"LABOUR", "labour@civileng.test", "Mahesh Labour", "+919000000005"},
            new String[]{"LABOUR_CONTRACTOR", "contractor@civileng.test", "Kiran Contractor", "+919000000006"},
            new String[]{"CIVIL_ENGINEER", "engineer@civileng.test", "Anita Engineer", "+919000000007"},
            new String[]{"ARCHITECT", "architect@civileng.test", "Priya Architect", "+919000000008"},
            new String[]{"SURVEYOR", "surveyor@civileng.test", "Vikram Surveyor", "+919000000009"},
            new String[]{"MATERIAL_SUPPLIER", "supplier@civileng.test", "Deepak Supplier", "+919000000010"}
    );

    /** The platform company's own staff, seeded into the operator tenant only. */
    static final List<String[]> PLATFORM_ACCOUNTS = List.of(
            new String[]{"PLATFORM_OWNER", PLATFORM_OWNER_EMAIL, "Platform Owner", OWNER_PHONE},
            new String[]{"PLATFORM_ADMIN", PLATFORM_ADMIN_EMAIL, "Platform Admin", "+919000000011"},
            new String[]{"PLATFORM_SUPPORT", PLATFORM_SUPPORT_EMAIL, "Platform Support", "+919000000012"}
    );

    static List<String[]> accountsFor(String tenantKey) {
        return com.civileng.marketplace.web.common.PlatformRoles.isOperatorTenant(tenantKey)
                ? PLATFORM_ACCOUNTS : TENANT_ACCOUNTS;
    }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Optional so this still works in a single-schema setup. Present whenever
     * {@code platform.tenant.enabled} is true, which is every real run of this service.
     */
    private final ObjectProvider<CrossTenantRunner> crossTenantRunner;

    /**
     * A transaction per tenant, opened inside the tenant's context rather than around the whole
     * sweep. One outer {@code @Transactional} would bind a single connection — and therefore a
     * single schema — for the entire run, so every tenant after the first would be seeded into the
     * first one's tables.
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * Seeds every active tenant, not just whichever schema the startup thread happened to hold.
     *
     * <p>This used to run once, unscoped. The result was that the bootstrap tenant got the full set
     * of dummy accounts and every tenant onboarded afterwards got none — so a new tenant could not
     * be signed into at all, and the multi-tenant behaviour that the rest of the platform is built
     * around could not be demonstrated in a browser.
     *
     * <p>One tenant's failure is logged and the sweep continues, so a single tenant with a missing
     * roles table cannot stop the rest from being seeded.
     */
    @Override
    public void run(ApplicationArguments args) {
        CrossTenantRunner runner = crossTenantRunner.getIfAvailable();
        if (runner == null) {
            // Single-schema setup: seed whatever the current context points at, as before.
            transactionTemplate.executeWithoutResult(status -> seedCurrentTenant("default"));
            return;
        }
        runner.forEachTenant("Dev user seeder", tenantKey ->
                transactionTemplate.executeWithoutResult(status -> seedCurrentTenant(tenantKey)));
    }

    /** Seeds the tenant whose schema the calling thread is bound to. */
    private void seedCurrentTenant(String tenantKey) {
        Map<String, Role> rolesByName = roleRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getName, r -> r));

        if (com.civileng.marketplace.web.common.PlatformRoles.isOperatorTenant(tenantKey)) {
            renamePlatformStaff();
        } else {
            reconcileOwner();
        }

        List<String[]> accounts = accountsFor(tenantKey);
        int created = 0;
        for (String[] account : accounts) {
            String roleName = account[0];
            String email = account[1];

            if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
                continue;
            }
            Role role = rolesByName.get(roleName);
            if (role == null) {
                log.warn("Dev seeder [{}]: role {} not found, skipping {}",
                        tenantKey, roleName, email);
                continue;
            }
            // Phones are unique per schema, so a hand-edited or renamed account already holding
            // this number would fail the insert and roll back the whole tenant's seeding.
            if (userRepository.existsByPhoneAndIsDeletedFalse(account[3])) {
                log.warn("Dev seeder [{}]: phone {} already in use, skipping {}",
                        tenantKey, account[3], email);
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
            log.info("Dev seeder [{}]: created {} dummy accounts (password: {})",
                    tenantKey, created, DEFAULT_PASSWORD);
        } else {
            log.info("Dev seeder [{}]: all {} dummy accounts already present",
                    tenantKey, accounts.size());
        }
    }

    static String passwordFor(String email) {
        if (PLATFORM_OWNER_EMAIL.equals(email) || PLATFORM_ADMIN_EMAIL.equals(email)
                || PLATFORM_SUPPORT_EMAIL.equals(email)) {
            return PLATFORM_PASSWORD;
        }
        return OWNER_EMAIL.equals(email) ? OWNER_PASSWORD : DEFAULT_PASSWORD;
    }

    /** Moves the operator tenant's staff from their old seeded addresses to the current ones. */
    private void renamePlatformStaff() {
        PLATFORM_RENAMES.forEach((from, to) ->
                userRepository.findByEmailAndIsDeletedFalse(from).ifPresent(user -> {
                    if (userRepository.existsByEmailAndIsDeletedFalse(to)) {
                        log.info("Dev seeder [platform]: {} left as-is, {} already exists", from, to);
                        return;
                    }
                    user.setEmail(to);
                    user.setPasswordHash(passwordEncoder.encode(PLATFORM_PASSWORD));
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    log.info("Dev seeder [platform]: {} renamed to {}", from, to);
                }));
    }

    /**
     * Moves an already-seeded owner onto the current address and password.
     *
     * <p>Without this, an environment seeded before the change keeps the old account: the
     * main loop only ever creates missing accounts, so the new address would be added as a
     * second owner alongside a stale one that still accepts the old password.
     */
    private void reconcileOwner() {
        userRepository.findByEmailAndIsDeletedFalse(LEGACY_OWNER_EMAIL).ifPresent(user -> {
            if (userRepository.existsByEmailAndIsDeletedFalse(OWNER_EMAIL)) {
                // Both exist — the current one is authoritative; nothing to migrate onto it.
                log.info("Dev seeder: legacy owner {} left as-is, {} already exists",
                        LEGACY_OWNER_EMAIL, OWNER_EMAIL);
                return;
            }
            user.setEmail(OWNER_EMAIL);
            user.setPasswordHash(passwordEncoder.encode(OWNER_PASSWORD));
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Dev seeder: owner migrated from {} to {}",
                    LEGACY_OWNER_EMAIL, OWNER_EMAIL);
        });

        // Keeps the password and mobile number correct if the account exists but predates
        // the current values.
        userRepository.findByEmailAndIsDeletedFalse(OWNER_EMAIL).ifPresent(user -> {
            boolean changed = false;
            if (!passwordEncoder.matches(OWNER_PASSWORD, user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(OWNER_PASSWORD));
                changed = true;
                log.info("Dev seeder: owner password reset to the configured dev value");
            }
            if (!OWNER_PHONE.equals(user.getPhone())
                    && !userRepository.existsByPhoneAndIsDeletedFalse(OWNER_PHONE)) {
                user.setPhone(OWNER_PHONE);
                user.setPhoneVerified(true);
                changed = true;
                log.info("Dev seeder: owner mobile number updated");
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
        for (String[] account : TENANT_ACCOUNTS) {
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
