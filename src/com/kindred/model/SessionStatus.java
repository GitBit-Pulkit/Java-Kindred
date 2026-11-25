package com.kindred.model;

/**
 * Life cycle of a learning session:
 *
 *   PENDING --(teacher accepts)--> ACCEPTED --(learner marks done)--> COMPLETED
 *      |                               (credits move learner -> teacher here)
 *      +--(teacher rejects)--> REJECTED
 *      +--(learner cancels)--> CANCELLED
 *
 * Stored in MySQL as an ENUM with the same names.
 */
// SYLLABUS: PreparedStatement parameter binding - status.name() is bound with setString in conditional UPDATEs
public enum SessionStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
