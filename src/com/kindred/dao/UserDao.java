package com.kindred.dao;

import com.kindred.db.DBConnection;
import com.kindred.model.Role;
import com.kindred.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the `users` table.
 * Only this class contains SQL for users. Services call these methods.
 *
 * Methods that take a Connection parameter run INSIDE a transaction owned by
 * the caller (AuthService.register, CreditService.completeSession). The caller
 * decides when to commit or roll back.
 */
// SYLLABUS: CRUD Operations - implements GenericDao<User, Integer>
public class UserDao implements GenericDao<User, Integer> {

    private static final String SELECT_COLUMNS =
            "SELECT user_id, username, full_name, password_hash, role, credits, created_at FROM users ";

    // ---------------------------------------------------------------- CREATE

    @Override
    public Integer create(User user) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return create(conn, user);
        }
    }

    /** INSERT inside the caller's transaction. Returns the AUTO_INCREMENT id. */
    public int create(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (username, full_name, password_hash, role, credits) VALUES (?, ?, ?, ?, ?)";
        // SYLLABUS: PreparedStatement - '?' placeholders prevent SQL injection.
        // (Statement.RETURN_GENERATED_KEYS is only a constant; no plain Statement object is used.)
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // SYLLABUS: Parameter binding - setString / setInt fill the '?' in order
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setInt(5, user.getCredits());
            ps.executeUpdate(); // SYLLABUS: executeUpdate() - INSERT

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No id was generated for the new user.");
        }
    }

    // ---------------------------------------------------------------- READ

    @Override
    public Optional<User> findById(Integer id) throws SQLException {
        String sql = SELECT_COLUMNS + "WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            // SYLLABUS: executeQuery() returns a ResultSet
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUsername(conn, username);
        }
    }

    public Optional<User> findByUsername(Connection conn, String username) throws SQLException {
        String sql = SELECT_COLUMNS + "WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        String sql = SELECT_COLUMNS + "ORDER BY user_id";
        List<User> users = new ArrayList<>(); // SYLLABUS: Generics - diamond operator
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {           // move row by row through the ResultSet
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    /** Used during registration: the very first user becomes ADMIN. */
    public int countUsers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Reads a balance AND locks the row until the transaction ends (FOR UPDATE).
     * No other transaction can change this wallet until we commit or roll back.
     */
    public int getCreditsForUpdate(Connection conn, int userId) throws SQLException {
        String sql = "SELECT credits FROM users WHERE user_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("User #" + userId + " no longer exists.");
                }
                return rs.getInt("credits"); // SYLLABUS: ResultSet typed getter getInt()
            }
        }
    }

    // ---------------------------------------------------------------- UPDATE

    /** Updates the editable profile fields (full name and password hash). */
    @Override
    public boolean update(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, password_hash = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getPasswordHash());
            ps.setInt(3, user.getUserId());
            return ps.executeUpdate() == 1; // SYLLABUS: executeUpdate() returns rows affected
        }
    }

    /** Adds 'delta' (negative to subtract) to a wallet inside the caller's transaction. */
    public void adjustCredits(Connection conn, int userId, int delta) throws SQLException {
        String sql = "UPDATE users SET credits = credits + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, userId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Wallet of user #" + userId + " could not be updated.");
            }
        }
    }

    // ---------------------------------------------------------------- DELETE

    /** Deletes a user. Their skills, sessions and notifications go too (ON DELETE CASCADE). */
    @Override
    public boolean delete(Integer id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    // ---------------------------------------------------------------- mapping

    /** Converts the CURRENT ResultSet row into a User object. */
    // SYLLABUS: ResultSet mapping with typed getters (getInt, getString, getTimestamp)
    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getInt("credits"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
