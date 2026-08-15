package com.civileng.marketplace.admin.settings.service;

import com.civileng.marketplace.admin.settings.model.PlatformSetting;
import com.civileng.marketplace.admin.settings.repository.PlatformSettingRepository;
import com.civileng.marketplace.admin.settings.service.PlatformSettings.Definition;
import com.civileng.marketplace.admin.settings.service.PlatformSettings.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads and writes the platform's settings.
 *
 * <p>Every value is validated against its catalogue definition on the way in, so a value that is
 * stored is a value the platform can act on. Values are returned as strings in the shape the
 * catalogue declares — the console renders the editor from the type, so the two cannot disagree.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformSettingsService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private final PlatformSettingRepository repository;

    /** The whole settings screen: groups, their settings, and each one's current value. */
    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        Map<String, String> stored = storedValues();

        List<Map<String, Object>> groups = new ArrayList<>();
        for (String group : PlatformSettings.groups()) {
            List<Map<String, Object>> items = PlatformSettings.all().stream()
                    .filter(definition -> definition.group().equals(group))
                    .map(definition -> describe(definition, stored))
                    .toList();
            groups.add(Map.of("group", group, "settings", items));
        }
        return Map.of("groups", groups);
    }

    /**
     * Just the effective values, keyed by setting. This is what another service would read if it
     * ever needs to act on a setting, which is why it is a flat map and not the screen's shape.
     */
    @Transactional(readOnly = true)
    public Map<String, String> effectiveValues() {
        Map<String, String> stored = storedValues();
        Map<String, String> effective = new LinkedHashMap<>();
        PlatformSettings.all().forEach(definition ->
                effective.put(definition.key(), stored.getOrDefault(definition.key(), definition.defaultValue())));
        return effective;
    }

    /**
     * Applies the changes an admin submitted. Every value is validated before anything is written,
     * so a form with one bad field is rejected whole rather than half-saved.
     *
     * @return the settings screen as it now reads
     */
    @Transactional
    public Map<String, Object> update(Map<String, String> changes, Long adminId) {
        if (changes == null || changes.isEmpty()) return settings();

        Map<String, String> normalised = new LinkedHashMap<>();
        changes.forEach((key, value) ->
                normalised.put(key, normalise(PlatformSettings.require(key), value)));

        normalised.forEach((key, value) -> repository.save(new PlatformSetting(key, value, adminId)));
        log.info("Platform settings updated by admin {}: {}", adminId, normalised.keySet());
        return settings();
    }

    /** Drops a setting's stored row so it follows the shipped default again. */
    @Transactional
    public Map<String, Object> reset(String key) {
        PlatformSettings.require(key);
        repository.findById(key).ifPresent(repository::delete);
        log.info("Platform setting {} reset to its default", key);
        return settings();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, String> storedValues() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(PlatformSetting::getSettingKey, PlatformSetting::getSettingValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, Object> describe(Definition definition, Map<String, String> stored) {
        String value = stored.get(definition.key());
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("key", definition.key());
        described.put("label", definition.label());
        described.put("help", definition.help());
        described.put("type", definition.type().name());
        described.put("value", value != null ? value : definition.defaultValue());
        described.put("defaultValue", definition.defaultValue());
        // The console shows "Default" against an untouched setting, which is a different statement
        // from "happens to equal the default".
        described.put("customised", value != null);
        described.put("choices", definition.choices());
        described.put("min", definition.min());
        described.put("max", definition.max());
        return described;
    }

    /**
     * The value as it will be stored: trimmed, checked against its type, and canonicalised so that
     * "TRUE", "true" and "1" are all one stored value.
     */
    private static String normalise(Definition definition, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();

        if (value.isEmpty()) {
            // A cleared text field is a legitimate "not set" for the optional ones; anything else
            // would be a hole in a value the platform acts on.
            if (definition.type() == Type.TEXT || definition.type() == Type.EMAIL) return "";
            throw new IllegalArgumentException(definition.label() + " cannot be empty");
        }

        return switch (definition.type()) {
            case TEXT -> requireLength(definition, value);
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    throw new IllegalArgumentException(definition.label() + " must be an email address");
                }
                yield requireLength(definition, value);
            }
            case BOOLEAN -> {
                String lower = value.toLowerCase(Locale.ROOT);
                if (!List.of("true", "false", "1", "0", "yes", "no").contains(lower)) {
                    throw new IllegalArgumentException(definition.label() + " must be true or false");
                }
                yield Boolean.toString(List.of("true", "1", "yes").contains(lower));
            }
            case NUMBER, PERCENT -> {
                double parsed;
                try {
                    parsed = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(definition.label() + " must be a number");
                }
                if (definition.min() != null && parsed < definition.min()) {
                    throw new IllegalArgumentException(
                            definition.label() + " cannot be below " + trimZero(definition.min()));
                }
                if (definition.max() != null && parsed > definition.max()) {
                    throw new IllegalArgumentException(
                            definition.label() + " cannot be above " + trimZero(definition.max()));
                }
                yield trimZero(parsed);
            }
            case CHOICE -> {
                if (!definition.choices().contains(value)) {
                    throw new IllegalArgumentException(
                            definition.label() + " must be one of " + String.join(", ", definition.choices()));
                }
                yield value;
            }
        };
    }

    private static String requireLength(Definition definition, String value) {
        if (value.length() > 500) {
            throw new IllegalArgumentException(definition.label() + " can be at most 500 characters");
        }
        return value;
    }

    /** 10.0 -> "10", 12.5 -> "12.5" — a whole number should not be stored with a decimal tail. */
    private static String trimZero(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    /** Convenience for callers that want one setting rather than the whole map. */
    @Transactional(readOnly = true)
    public String value(String key) {
        Definition definition = PlatformSettings.require(key);
        return repository.findById(key)
                .map(PlatformSetting::getSettingValue)
                .orElse(definition.defaultValue());
    }

    /** Convenience for the boolean switches, which are the ones other code branches on. */
    @Transactional(readOnly = true)
    public boolean flag(String key) {
        return Boolean.parseBoolean(value(key));
    }
}
