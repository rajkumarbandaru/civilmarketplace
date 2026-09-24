package com.civileng.marketplace.auth.repository;

import com.civileng.marketplace.auth.entity.UserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    List<UserInvitation> findByUserIdAndUsedAtIsNull(Long userId);
}
