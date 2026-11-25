package com.kindred.model;

/**
 * Role of a user in Kindred.
 * USER  -> normal member (teach, learn, earn and spend credits)
 * ADMIN -> can also see every user and every transaction, and delete accounts.
 * The FIRST account ever registered automatically becomes ADMIN (see AuthService).
 *
 * Stored in MySQL as ENUM('USER','ADMIN').
 */
// SYLLABUS: ResultSet mapping - Role.valueOf(rs.getString("role")) converts the MySQL ENUM text
public enum Role {
    USER,
    ADMIN
}
