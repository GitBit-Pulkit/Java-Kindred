package com.kindred.service;

/**
 * A business-rule or database problem, carrying a message that is SAFE to show
 * the user ("Not enough credits", "Username already taken", ...).
 *
 * Services catch low-level SQLException / IOException and wrap them in this,
 * so the menu layer only has to catch ONE exception type and print getMessage().
 */
// SYLLABUS: Exception-safe handling - SQLException / IOException wrapped into one friendly checked exception
public class ServiceException extends Exception {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
