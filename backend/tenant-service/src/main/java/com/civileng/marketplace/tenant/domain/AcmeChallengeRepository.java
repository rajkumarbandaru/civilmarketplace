package com.civileng.marketplace.tenant.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcmeChallengeRepository extends JpaRepository<AcmeChallenge, String> {

    void deleteByHost(String host);
}
