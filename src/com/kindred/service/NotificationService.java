package com.kindred.service;

import com.kindred.dao.NotificationDao;
import com.kindred.model.Notification;

import java.sql.SQLException;
import java.util.List;

/**
 * Front door of the notification system. It owns the queue and the consumer thread.
 *
 *  SessionService --notifyUser()--> [ NotificationQueue ] --consume()--> NotificationConsumer --> MySQL + live alert
 *     (producer, main thread)          (bounded buffer)                 (background thread)
 */
public class NotificationService {

    private final NotificationQueue queue;
    private final NotificationConsumer consumer;
    private final NotificationDao notificationDao = new NotificationDao();

    public NotificationService(int queueCapacity) {
        this.queue = new NotificationQueue(queueCapacity);
        this.consumer = new NotificationConsumer(queue);
    }

    /** Starts the background thread (start() creates a new thread that runs run()). */
    // SYLLABUS: Multithreading - Thread.start() (and join() in shutdown())
    public void start() {
        consumer.start();
    }

    /** Tells the consumer who is logged in now (-1 = nobody), for live alerts. */
    public void setActiveUser(int userId) {
        consumer.setActiveUserId(userId);
    }

    /** PRODUCER call: puts a notification on the queue and returns at once. */
    public void notifyUser(int userId, String message) {
        try {
            queue.produce(new Notification(userId, message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<Notification> getInbox(int userId) throws ServiceException {
        try {
            return notificationDao.findByUser(userId);
        } catch (SQLException e) {
            throw new ServiceException("Could not load notifications: " + e.getMessage(), e);
        }
    }

    public int countUnread(int userId) {
        try {
            return notificationDao.countUnread(userId);
        } catch (SQLException e) {
            return 0;   // a counter is not worth crashing for
        }
    }

    public void markAllRead(int userId) throws ServiceException {
        try {
            notificationDao.markAllRead(userId);
        } catch (SQLException e) {
            throw new ServiceException("Could not update notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Graceful stop: no new notifications are accepted, the consumer saves
     * whatever is still queued, then we wait (join) for the thread to finish.
     */
    public void shutdown() {
        queue.shutdown();
        try {
            consumer.join(5000);   // wait at most 5 seconds for the consumer to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
