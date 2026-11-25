package com.kindred.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing with BCrypt (library: lib/jbcrypt-0.4.jar).
 *
 * - hash(): BCrypt adds a random SALT and is deliberately SLOW (work factor 12),
 *   so stolen hashes are very hard to brute-force. The same password gives a
 *   different hash every time because the salt differs.
 * - verify(): re-hashes the typed password with the salt stored inside the
 *   hash and compares the two.
 *
 * Only the 60-character hash is stored in users.password_hash, never the
 * plain password.
 */
// SYLLABUS: Authentication security - BCrypt hashing (jbcrypt JAR); no plain-text passwords
public final class PasswordUtil {

    private static final int WORK_FACTOR = 12;

    private PasswordUtil() { }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    /** True if the password matches. Returns false (no crash) if the stored hash is corrupted. */
    public static boolean verify(String plainPassword, String storedHash) {
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
