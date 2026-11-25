-- ============================================================
--  Kindred database schema (MySQL 8+)
--  You do NOT have to run this by hand: DatabaseInitializer creates
--  the same tables automatically on start-up. It is here for reference
--  and for anyone who prefers to set up the database manually.
-- ============================================================

CREATE DATABASE IF NOT EXISTS kindred;
USE kindred;

CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(20) NOT NULL UNIQUE,
    full_name     VARCHAR(60) NOT NULL,
    password_hash CHAR(60)    NOT NULL,              -- BCrypt hash, never the plain password
    role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    credits       INT NOT NULL DEFAULT 0,            -- SkillCredit wallet
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_credits_non_negative CHECK (credits >= 0)
);

CREATE TABLE IF NOT EXISTS skills (
    skill_id    INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    skill_name  VARCHAR(40) NOT NULL,
    skill_type  ENUM('TEACH','LEARN') NOT NULL,
    proficiency TINYINT NOT NULL,
    CONSTRAINT chk_proficiency CHECK (proficiency BETWEEN 1 AND 5),
    CONSTRAINT uq_user_skill UNIQUE (user_id, skill_name, skill_type),
    CONSTRAINT fk_skill_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sessions (
    session_id   INT AUTO_INCREMENT PRIMARY KEY,
    learner_id   INT NOT NULL,
    teacher_id   INT NOT NULL,
    skill_name   VARCHAR(40) NOT NULL,
    hours        INT NOT NULL,
    status       ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_hours CHECK (hours > 0),
    CONSTRAINT fk_session_learner FOREIGN KEY (learner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transactions (
    txn_id       INT AUTO_INCREMENT PRIMARY KEY,
    session_id   INT NULL UNIQUE,                    -- a session can be paid only once
    from_user_id INT NULL,                           -- NULL = SYSTEM (welcome bonus)
    to_user_id   INT NULL,
    amount       INT NOT NULL,
    txn_type     ENUM('WELCOME_BONUS','SESSION_PAYMENT') NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_amount CHECK (amount > 0),
    CONSTRAINT fk_txn_session FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_from FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_to FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    message         VARCHAR(255) NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
