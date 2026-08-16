package com.civileng.marketplace.notification.model;

/** How a notification reached (or failed to reach) its recipient. */
public enum NotificationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    /**
     * The bell in the app. Written straight to our own table, so it has no provider and no
     * delivery callback — an IN_APP row is DELIVERED the moment it exists.
     */
    IN_APP
}
