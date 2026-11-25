package com.kindred.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Creates all Kindred tables on start-up if they do not exist yet, so the app
 * works on a fresh MySQL installation without running any script by hand.
 * (The same SQL is in sql/schema.sql for reference.)
 *
 * Data-safety rules are enforced by MySQL itself:
 *   - CHECK (credits >= 0)          -> a wallet can never go negative
 *   - UNIQUE (user_id, skill_name, skill_type) -> no duplicate skills
 *   - UNIQUE (session_id) on transactions -> a session can be paid only once
 *   - FOREIGN KEYs with ON DELETE CASCADE / SET NULL -> no orphan rows
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() { }

    private static final String[] CREATE_TABLES = {
        "CREATE TABLE IF NOT EXISTS users ("
            + " user_id INT AUTO_INCREMENT PRIMARY KEY,"
            + " username VARCHAR(20) NOT NULL UNIQUE,"
            + " full_name VARCHAR(60) NOT NULL,"
            + " password_hash CHAR(60) NOT NULL,"
            + " role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',"
            + " credits INT NOT NULL DEFAULT 0,"
            + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " CONSTRAINT chk_credits_non_negative CHECK (credits >= 0)"
            + ")",

        "CREATE TABLE IF NOT EXISTS skills ("
            + " skill_id INT AUTO_INCREMENT PRIMARY KEY,"
            + " user_id INT NOT NULL,"
            + " skill_name VARCHAR(40) NOT NULL,"
            + " skill_type ENUM('TEACH','LEARN') NOT NULL,"
            + " proficiency TINYINT NOT NULL,"
            + " CONSTRAINT chk_proficiency CHECK (proficiency BETWEEN 1 AND 5),"
            + " CONSTRAINT uq_user_skill UNIQUE (user_id, skill_name, skill_type),"
            + " CONSTRAINT fk_skill_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE"
            + ")",

        "CREATE TABLE IF NOT EXISTS sessions ("
            + " session_id INT AUTO_INCREMENT PRIMARY KEY,"
            + " learner_id INT NOT NULL,"
            + " teacher_id INT NOT NULL,"
            + " skill_name VARCHAR(40) NOT NULL,"
            + " hours INT NOT NULL,"
            + " status ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',"
            + " requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + " CONSTRAINT chk_hours CHECK (hours > 0),"
            + " CONSTRAINT fk_session_learner FOREIGN KEY (learner_id) REFERENCES users(user_id) ON DELETE CASCADE,"
            + " CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE CASCADE"
            + ")",

        "CREATE TABLE IF NOT EXISTS transactions ("
            + " txn_id INT AUTO_INCREMENT PRIMARY KEY,"
            + " session_id INT NULL UNIQUE,"
            + " from_user_id INT NULL,"
            + " to_user_id INT NULL,"
            + " amount INT NOT NULL,"
            + " txn_type ENUM('WELCOME_BONUS','SESSION_PAYMENT') NOT NULL,"
            + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " CONSTRAINT chk_amount CHECK (amount > 0),"
            + " CONSTRAINT fk_txn_session FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE SET NULL,"
            + " CONSTRAINT fk_txn_from FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE SET NULL,"
            + " CONSTRAINT fk_txn_to FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE SET NULL"
            + ")",

        "CREATE TABLE IF NOT EXISTS notifications ("
            + " notification_id INT AUTO_INCREMENT PRIMARY KEY,"
            + " user_id INT NOT NULL,"
            + " message VARCHAR(255) NOT NULL,"
            + " is_read BOOLEAN NOT NULL DEFAULT FALSE,"
            + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE"
            + ")"
    };

    /** Runs every CREATE TABLE IF NOT EXISTS statement. Returns false on failure. */
    public static boolean createTables() {
        try (Connection conn = DBConnection.getConnection()) {
            for (String ddl : CREATE_TABLES) {
                // SYLLABUS: PreparedStatement - also used for DDL (no plain Statement anywhere)
                try (PreparedStatement ps = conn.prepareStatement(ddl)) {
                    ps.executeUpdate(); // SYLLABUS: executeUpdate()
                }
            }
            return true;
        } catch (SQLException e) {
            System.out.println("[DB] Could not create tables: " + e.getMessage());
            return false;
        }
    }
}
