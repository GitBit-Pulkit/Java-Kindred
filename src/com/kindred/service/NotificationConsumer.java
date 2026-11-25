package com.kindred.service;

import com.kindred.dao.NotificationDao;
import com.kindred.model.Notification;

import java.sql.SQLException;

/**
 * The CONSUMER: a raw Thread that runs in the background for the app's whole life.
 *
 * Loop: take the next Notification from the queue (it waits if the queue is
 * empty) -> save it in MySQL (the recipient's inbox) -> if the recipient is the
 * user currently logged in, print a live alert.
 *
 * The slow part (a database INSERT) happens HERE and not on the main thread,
 * so the menu never freezes while notifications are delivered.
 */
// SYLLABUS: Multithreading - raw Thread (extends Thread, overrides run())
public class NotificationConsumer extends Thread {

    private final NotificationQueue queue;
    private final NotificationDao notificationDao = new NotificationDao();

    // volatile: a change made by the main thread is immediately visible to this thread
    private volatile int activeUserId = -1;

    public NotificationConsumer(NotificationQueue queue) {
        super("Kindred-Notification-Consumer");  // thread name, handy when debugging
        this.queue = queue;
    }

    public void setActiveUserId(int userId) {
        this.activeUserId = userId;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Notification next = queue.consume();   // waits while the queue is empty
                if (next == null) {
                    break;                             // queue shut down and drained
                }
                deliver(next);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();        // keep the interrupt flag set
        }
    }

    private void deliver(Notification n) {
        try {
            notificationDao.insert(n);                 // this thread uses its OWN DB connection
        } catch (SQLException e) {
            System.out.println("\n  [Notification] could not be saved: " + e.getMessage());
        }
        if (n.getUserId() == activeUserId) {
            System.out.println("\n  >> [Kindred Alert] " + n.getMessage());
        }
    }
}
