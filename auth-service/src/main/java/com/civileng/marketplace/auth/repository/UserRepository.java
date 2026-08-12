package com.civileng.marketplace.auth.repository;

import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByPhoneAndIsDeletedFalse(String phone);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByPhoneAndIsDeletedFalse(String phone);

    // Admin: search by name or email
    Page<User> findByNameContainingOrEmailContaining(String name, String email, Pageable pageable);

    // Admin: DB-level filtered query with pagination (fixes pagination metadata bug)
    @Query("SELECT u FROM User u WHERE " +
           "(:search IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:role IS NULL OR u.role.name = :role) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> findAdminUsers(@Param("search") String search,
                              @Param("role") String role,
                              @Param("status") UserStatus status,
                              Pageable pageable);

    // Admin: count by status
    long countByStatus(UserStatus status);

    /**
     * Live user count per role name, for callers that need the whole breakdown in one query
     * (the UI-config workspace list). Roles with no users are absent — the caller pairs this
     * against the roles table rather than assuming every role appears.
     */
    @Query("SELECT u.role.name, COUNT(u) FROM User u WHERE u.isDeleted = false GROUP BY u.role.name")
    List<Object[]> countUsersByRoleName();

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE User u SET u.loginAttempts = u.loginAttempts + 1 WHERE u.id = :userId")
    void incrementLoginAttempts(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.loginAttempts = 0, u.lockedUntil = null WHERE u.id = :userId")
    void resetLoginAttempts(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :userId")
    void lockAccount(@Param("userId") Long userId,
                     @Param("lockedUntil") LocalDateTime lockedUntil);
}
