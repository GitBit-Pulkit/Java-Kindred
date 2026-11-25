package com.kindred.dao;

import com.kindred.db.DBConnection;
import com.kindred.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the `notifications` table (each user's inbox).
 * insert() is called by the BACKGROUND NotificationConsumer thread with its own
 * Connection. The read methods are called by the main thread.
 */
public class NotificationDao {

    public void insert(Notification n) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, message) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             // SYLLABUS: PreparedStatement + parameter binding
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n.getUserId());
            ps.setString(2, n.getMessage());
            ps.executeUpdate();
        }
    }

    /** Newest first. */
    public List<Notification> findByUser(int userId) throws SQLException {
        String sql = "SELECT notification_id, user_id, message, is_read, created_at "
                   + "FROM notifications WHERE user_id = ? ORDER BY notification_id DESC";
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // SYLLABUS: ResultSet typed getters (getBoolean, getTimestamp ...)
                    list.add(new Notification(
                            rs.getInt("notification_id"),
                            rs.getInt("user_id"),
                            rs.getString("message"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return list;
    }

    public int countUnread(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int markAllRead(int userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }
}
