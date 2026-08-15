package com.civileng.marketplace.admin.settings.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One platform-wide setting, stored as text against its key.
 *
 * <p>Key/value rather than a wide table with a column per setting: settings are added far more
 * often than they are read in bulk, and a new one should be a row in the catalogue, not a
 * migration. The catalogue in {@code PlatformSettings} is what gives each key its type, its
 * default and its validation — a row here with no catalogue entry is ignored.
 *
 * <p>Absence means "the shipped default". Nothing is seeded, so changing a default in a release
 * actually reaches every platform that never overrode it.
 */
@Entity
@Table(name = "admin_platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 64)
    private String settingKey;

    /** Always text; the catalogue's type decides how it is parsed back out. */
    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PlatformSetting(String settingKey, String settingValue, Long updatedBy) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
    }
}
