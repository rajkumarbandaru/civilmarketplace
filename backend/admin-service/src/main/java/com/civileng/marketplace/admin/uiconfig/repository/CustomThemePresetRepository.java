package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.CustomThemePreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Keyed by the slug generated from the preset's label. */
@Repository
public interface CustomThemePresetRepository extends JpaRepository<CustomThemePreset, String> {

    Optional<CustomThemePreset> findByLabelIgnoreCase(String label);

    java.util.List<CustomThemePreset> findAllByOrderByLabelAsc();
}
