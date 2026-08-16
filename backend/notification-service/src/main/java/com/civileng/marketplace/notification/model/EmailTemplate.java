package com.civileng.marketplace.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * An admin-editable override for one transactional email.
 *
 * <p>For the built-in templates the key matches the classpath file under
 * {@code resources/templates/email}; deactivating a system-owned row reverts that email to the
 * shipped file rather than disabling the mail.
 */
@Entity
@Table(name = "email_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, length = 120, unique = true)
    private String templateKey;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Lob
    @Column(name = "html_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String htmlBody;

    /** JSON object of placeholder name to example value, used to render the preview. */
    @Lob
    @Column(name = "sample_variables", columnDefinition = "TEXT")
    private String sampleVariables;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "system_owned", nullable = false)
    @Builder.Default
    private Boolean systemOwned = false;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
