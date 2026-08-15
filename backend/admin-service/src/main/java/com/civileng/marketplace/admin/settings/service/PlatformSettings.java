package com.civileng.marketplace.admin.settings.service;

import java.util.List;

/**
 * The catalogue of platform settings: every key the console can show, with the type that decides
 * how it is edited and validated, and the default that applies until someone overrides it.
 *
 * <p>Kept as code rather than data because each key means something to the platform — adding one
 * here without the code that reads it would give an admin a switch wired to nothing.
 */
public final class PlatformSettings {

    private PlatformSettings() {
    }

    public enum Type { TEXT, EMAIL, NUMBER, PERCENT, BOOLEAN, CHOICE }

    /**
     * @param key         stable identifier, stored as the row key
     * @param group       which card on the settings screen it appears under
     * @param label       what the admin reads
     * @param help        one line explaining the consequence of changing it
     * @param type        how it is edited and validated
     * @param defaultValue what applies when no row exists
     * @param choices     allowed values for {@link Type#CHOICE}, empty otherwise
     * @param min         inclusive lower bound for NUMBER/PERCENT
     * @param max         inclusive upper bound for NUMBER/PERCENT
     */
    public record Definition(
            String key, String group, String label, String help, Type type,
            String defaultValue, List<String> choices, Double min, Double max) {

        static Definition text(String key, String group, String label, String help, String defaultValue) {
            return new Definition(key, group, label, help, Type.TEXT, defaultValue, List.of(), null, null);
        }

        static Definition email(String key, String group, String label, String help, String defaultValue) {
            return new Definition(key, group, label, help, Type.EMAIL, defaultValue, List.of(), null, null);
        }

        static Definition number(String key, String group, String label, String help,
                                 String defaultValue, double min, double max) {
            return new Definition(key, group, label, help, Type.NUMBER, defaultValue, List.of(), min, max);
        }

        static Definition percent(String key, String group, String label, String help, String defaultValue) {
            return new Definition(key, group, label, help, Type.PERCENT, defaultValue, List.of(), 0d, 100d);
        }

        static Definition bool(String key, String group, String label, String help, boolean defaultValue) {
            return new Definition(key, group, label, help, Type.BOOLEAN,
                    Boolean.toString(defaultValue), List.of(), null, null);
        }

        static Definition choice(String key, String group, String label, String help,
                                 String defaultValue, List<String> choices) {
            return new Definition(key, group, label, help, Type.CHOICE, defaultValue, choices, null, null);
        }
    }

    public static final String GROUP_GENERAL = "General";
    public static final String GROUP_COMMERCE = "Commerce";
    public static final String GROUP_BOOKINGS = "Bookings";
    public static final String GROUP_NOTIFICATIONS = "Notifications";
    public static final String GROUP_ACCESS = "Access";

    private static final List<Definition> ALL = List.of(
            Definition.text("platform.name", GROUP_GENERAL, "Platform name",
                    "Used in emails and notifications. The console's own wordmark is set under Theme & UI style.",
                    "CivEng Marketplace"),
            Definition.email("platform.supportEmail", GROUP_GENERAL, "Support email",
                    "Where users are told to write when something goes wrong.",
                    "support@civeng.example"),
            Definition.text("platform.supportPhone", GROUP_GENERAL, "Support phone",
                    "Shown alongside the support email. Leave empty to show email only.", ""),
            Definition.choice("platform.currency", GROUP_GENERAL, "Currency",
                    "The currency every amount on the platform is stated in.",
                    "INR", List.of("INR", "USD", "EUR", "GBP", "AED")),
            Definition.choice("platform.timezone", GROUP_GENERAL, "Time zone",
                    "The zone reports and schedules are reckoned in.",
                    "Asia/Kolkata", List.of("Asia/Kolkata", "Asia/Dubai", "UTC",
                            "Europe/London", "America/New_York")),

            Definition.percent("commerce.platformFeePercent", GROUP_COMMERCE, "Platform fee",
                    "The platform's cut of each booking, as a percentage of the service amount.", "10"),
            Definition.percent("commerce.gstPercent", GROUP_COMMERCE, "GST",
                    "Tax added on top of the service amount and the platform fee.", "18"),
            Definition.number("commerce.minimumBookingAmount", GROUP_COMMERCE, "Minimum booking amount",
                    "Bookings below this amount are refused at checkout.", "500", 0, 1_000_000),
            Definition.number("commerce.payoutDelayDays", GROUP_COMMERCE, "Payout delay (days)",
                    "How long a completed booking's money is held before it is paid out.", "3", 0, 60),

            Definition.number("bookings.autoCancelHours", GROUP_BOOKINGS, "Auto-cancel unpaid after (hours)",
                    "An unpaid booking is cancelled once it has waited this long. 0 disables it.",
                    "24", 0, 720),
            Definition.number("bookings.maxActivePerCustomer", GROUP_BOOKINGS, "Active bookings per customer",
                    "How many bookings one customer may have open at once.", "5", 1, 100),
            Definition.number("bookings.cancellationWindowHours", GROUP_BOOKINGS, "Free cancellation window (hours)",
                    "How long before the scheduled time a customer may cancel without a fee.", "12", 0, 720),
            Definition.bool("bookings.autoAssignEnabled", GROUP_BOOKINGS, "Auto-assign workers",
                    "Assign a matching worker automatically instead of waiting for one to accept.", false),

            Definition.bool("notifications.emailEnabled", GROUP_NOTIFICATIONS, "Email notifications",
                    "Send transactional email. Turning this off silences every email the platform sends.", true),
            Definition.bool("notifications.smsEnabled", GROUP_NOTIFICATIONS, "SMS notifications",
                    "Send SMS for booking and payment events.", true),
            Definition.bool("notifications.pushEnabled", GROUP_NOTIFICATIONS, "Push notifications",
                    "Send push notifications to the mobile apps.", true),
            Definition.email("notifications.adminAlertEmail", GROUP_NOTIFICATIONS, "Admin alert email",
                    "Where disputes and failed payouts are reported. Leave empty to use the support email.", ""),

            Definition.bool("access.registrationOpen", GROUP_ACCESS, "Open registration",
                    "Allow new sign-ups. Turning this off leaves existing users unaffected.", true),
            Definition.bool("access.requireEmailVerification", GROUP_ACCESS, "Require email verification",
                    "New accounts must confirm their email before they can book.", true),
            Definition.bool("access.requireWorkerApproval", GROUP_ACCESS, "Manually approve workers",
                    "A worker cannot take bookings until an admin approves their profile.", true),
            Definition.bool("access.maintenanceMode", GROUP_ACCESS, "Maintenance mode",
                    "Show a maintenance notice to everyone except admins.", false));

    public static List<Definition> all() {
        return ALL;
    }

    /** The groups in the order the screen renders them. */
    public static List<String> groups() {
        return List.of(GROUP_GENERAL, GROUP_COMMERCE, GROUP_BOOKINGS, GROUP_NOTIFICATIONS, GROUP_ACCESS);
    }

    public static Definition require(String key) {
        return ALL.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No such setting: " + key + " — settings come from the catalogue, "
                                + "they cannot be invented by an admin"));
    }
}
