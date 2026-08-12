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
 * environment. Every account shares the password {@value #DEFAULT_PASSWORD}.
 */
@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
@Slf4j
public class DevUserSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "Password123!";

    /** role name -> {email, display name, phone} */
    private static final List<String[]> DUMMY_ACCOUNTS = List.of(
            new String[]{"SUPER_ADMIN", "superadmin@civileng.test", "Super Admin", "9000000001"},
            new String[]{"ADMIN", "admin@civileng.test", "Platform Admin", "9000000002"},
            new String[]{"CUSTOMER", "customer@civileng.test", "Ravi Customer", "9000000003"},
            new String[]{"WORKER", "worker@civileng.test", "Suresh Worker", "9000000004"},
            new String[]{"LABOUR", "labour@civileng.test", "Mahesh Labour", "9000000005"},
            new String[]{"LABOUR_CONTRACTOR", "contractor@civileng.test", "Kiran Contractor", "9000000006"},
            new String[]{"CIVIL_ENGINEER", "engineer@civileng.test", "Anita Engineer", "9000000007"},
            new String[]{"ARCHITECT", "architect@civileng.test", "Priya Architect", "9000000008"},
            new String[]{"SURVEYOR", "surveyor@civileng.test", "Vikram Surveyor", "9000000009"},
            new String[]{"MATERIAL_SUPPLIER", "supplier@civileng.test", "Deepak Supplier", "9000000010"}
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Role> rolesByName = roleRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getName, r -> r));

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
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
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
}
