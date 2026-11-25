package com.kindred.util;

/**
 * Thrown by InputHelper when the console input stream ends (for example Ctrl+Z
 * on Windows or end of a piped file). KindredApp catches it and shuts down
 * cleanly instead of crashing with NoSuchElementException.
 */
// SYLLABUS: Exception-safe handling - custom unchecked exception for a graceful exit
public class InputClosedException extends RuntimeException {
    public InputClosedException() {
        super("Console input was closed.");
    }
}
