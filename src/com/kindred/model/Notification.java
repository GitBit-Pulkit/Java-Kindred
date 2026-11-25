package com.kindred.model;

import java.time.LocalDateTime;

/**
 * An alert for a user ("alice requested a Java session", "your session was accepted", ...).
 *
 * Flow: a service PRODUCES a Notification into NotificationQueue (main thread),
 * and the background NotificationConsumer thread saves it into the
 * `notifications` table and prints it live if the recipient is logged in.
 */
// SYLLABUS: Producer-consumer - the item passed from producer to consumer through NotificationQueue
public class Notification {

    private int notificationId;
    private final int userId;
    private final String message;
    private boolean read;
    private LocalDateTime createdAt;

    /** A new notification that is not saved yet. */
    public Notification(int userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    /** A notification loaded from the database. */
    public Notification(int notificationId, int userId, String message, boolean read, LocalDateTime createdAt) {
        this(userId, message);
        this.notificationId = notificationId;
        this.read = read;
        this.createdAt = createdAt;
    }

    public int getNotificationId()     { return notificationId; }
    public int getUserId()             { return userId; }
    public String getMessage()         { return message; }
    public boolean isRead()            { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return (read ? "   " : "(*) ") + message;
    }
}
