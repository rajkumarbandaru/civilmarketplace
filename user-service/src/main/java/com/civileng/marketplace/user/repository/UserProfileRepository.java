package com.civileng.marketplace.user.repository;

import com.civileng.marketplace.user.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    List<UserProfile> findByCityAndIsAvailableTrue(String city);

    List<UserProfile> findByIsAvailableTrue();

    @Query("SELECT u FROM UserProfile u WHERE u.isAvailable = true AND " +
           "(:city IS NULL OR u.city = :city)")
    List<UserProfile> findAvailableProfiles(@Param("city") String city);

    boolean existsByUserId(Long userId);

    // Admin stats
    long countByIsVerifiedTrue();

    long countByIsAvailableTrue();
}
