package com.civileng.marketplace.notification.repository;

import com.civileng.marketplace.notification.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByTemplateKey(String templateKey);

    boolean existsByTemplateKey(String templateKey);

    List<EmailTemplate> findAllByOrderBySystemOwnedDescNameAsc();
}
