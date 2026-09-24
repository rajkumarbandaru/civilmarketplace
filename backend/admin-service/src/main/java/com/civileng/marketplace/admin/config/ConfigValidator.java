package com.civileng.marketplace.admin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks one document's content against its schema and override policy (04 §6.1, validators 1, 2
 * and 4). Errors block the publish; warnings are reported and stored with the version.
 */
public final class ConfigValidator {

    private static final Pattern HEX = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    /** Absolute http(s) or app-relative. Anything else — javascript:, data: — is refused. */
    private static final Pattern URL = Pattern.compile("^(https?://[^\\s]+|/[^\\s]*)$");

    private ConfigValidator() {
    }

    public record Report(List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static Report validate(ConfigDocument document, ConfigScope scope, Map<String, Object> content) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        content.forEach((key, value) -> {
            ConfigDocument.KeySpec spec = document.keys().get(key);
            String where = document.key() + "." + key;
            if (spec == null) {
                errors.add(where + " is not a known setting");
                return;
            }
            if (value == null) return;
            if (!spec.overridableAt().contains(scope.level())) {
                errors.add(where + " cannot be set at " + scope.level() + " level");
                return;
            }
            switch (spec.type()) {
                case TEXT -> {
                    if (!(value instanceof String s)) errors.add(where + " must be text");
                    else if (s.length() > spec.max()) errors.add(where + " can be at most " + spec.max() + " characters");
                }
                case URL -> {
                    if (!(value instanceof String s) || !URL.matcher(s).matches()) {
                        errors.add(where + " must be an http(s) URL or a path starting with /");
                    } else if (s.length() > spec.max()) {
                        errors.add(where + " can be at most " + spec.max() + " characters");
                    }
                }
                case COLOR -> {
                    if (!(value instanceof String s) || !HEX.matcher(s).matches()) {
                        errors.add(where + " must be a hex colour like #1a73e8");
                    }
                }
                case ENUM -> {
                    if (!spec.allowed().contains(String.valueOf(value))) {
                        errors.add(where + " must be one of " + String.join(", ", spec.allowed()));
                    }
                }
                case INTEGER -> {
                    if (!(value instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                        errors.add(where + " must be a whole number");
                    } else if (n.intValue() < spec.min() || n.intValue() > spec.max()) {
                        errors.add(where + " must be between " + spec.min() + " and " + spec.max());
                    }
                }
            }
        });
        if (document == ConfigDocument.THEME && content.get("primaryColor") instanceof String primary
                && HEX.matcher(primary).matches()) {
            double ratio = contrast(primary, "#FFFFFF");
            if (ratio < 3.0) {
                warnings.add(String.format("theme.primaryColor %s gives white button text a contrast of %.1f:1 "
                        + "(WCAG asks for at least 3:1 on large text, 4.5:1 on body text)", primary, ratio));
            }
        }
        return new Report(errors, warnings);
    }

    /** WCAG 2.x contrast ratio between two hex colours (alpha ignored). */
    static double contrast(String a, String b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1, 7), 16);
        double r = channel((rgb >> 16) & 0xFF), g = channel((rgb >> 8) & 0xFF), bl = channel(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
    }

    private static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
