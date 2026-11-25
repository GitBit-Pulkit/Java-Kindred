package com.kindred.service;

import com.kindred.model.Notification;

import java.util.LinkedList;

/**
 * BOUNDED BUFFER shared by the PRODUCER (main thread: services creating
 * notifications) and the CONSUMER (the background NotificationConsumer thread).
 *
 * Coordination uses a private lock object with:
 *   synchronized (lock) { ... } -> only one thread inside at a time
 *   lock.wait()                 -> release the lock and sleep until notified
 *   lock.notifyAll()            -> wake every thread waiting on the lock
 *
 * GUARDED BLOCKS: every wait() sits inside a while loop that re-checks its
 * condition after waking (a thread can wake up "spuriously", or another thread
 * may have changed the queue first). An if would not be safe here.
 */
public class NotificationQueue {

    private final LinkedList<Notification> queue = new LinkedList<>();
    private final int capacity;
    private final Object lock = new Object();   // the monitor both threads synchronize on
    private boolean shutdown = false;

    public NotificationQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** PRODUCER side: adds a notification, waiting while the queue is full. */
    public void produce(Notification notification) throws InterruptedException {
        // SYLLABUS: synchronized block
        synchronized (lock) {
            // SYLLABUS: Guarded block - wait() inside a while loop
            while (queue.size() == capacity && !shutdown) {
                lock.wait();                       // SYLLABUS: wait() releases the lock
            }
            if (shutdown) {
                return;                            // app is closing, no new work accepted
            }
            queue.addLast(notification);
            lock.notifyAll();                      // SYLLABUS: notifyAll() wakes the consumer
        }
    }

    /**
     * CONSUMER side: takes the oldest notification, waiting while the queue is empty.
     * Returns null only when shut down AND fully drained, so nothing queued is lost.
     */
    public Notification consume() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty() && !shutdown) {
                lock.wait();
            }
            if (queue.isEmpty()) {
                return null;                       // shutdown and nothing left -> consumer stops
            }
            Notification next = queue.removeFirst();
            lock.notifyAll();                      // wakes a producer that was waiting for space
            return next;
        }
    }

    /** Stops accepting new items and wakes every waiting thread so they can finish. */
    public void shutdown() {
        synchronized (lock) {
            shutdown = true;
            lock.notifyAll();
        }
    }
}
