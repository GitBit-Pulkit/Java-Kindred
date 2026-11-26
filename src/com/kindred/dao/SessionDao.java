package com.kindred.dao;

import com.kindred.db.DBConnection;
import com.kindred.model.SessionStatus;
import com.kindred.model.SkillSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the `sessions` table.
 * `users` is JOINed twice (as learner and as teacher) to get both usernames.
 * Status changes use a CONDITIONAL update:
 *    UPDATE ... SET status = 'ACCEPTED' WHERE session_id = ? AND status = 'PENDING'
 * If the row was already changed, 0 rows are affected and the service refuses
 * the action. This stops double-accepting or double-completing a session.
 */


//session dao
// SYLLABUS: CRUD Operations - implements GenericDao<SkillSession, Integer>
public class SessionDao implements GenericDao<SkillSession, Integer> {

    private static final String SELECT_JOIN =
            "SELECT s.session_id, s.learner_id, l.username AS learner_name, s.teacher_id, "
          + "t.username AS teacher_name, s.skill_name, s.hours, s.status, s.requested_at, s.updated_at "
          + "FROM sessions s "
          + "JOIN users l ON s.learner_id = l.user_id "
          + "JOIN users t ON s.teacher_id = t.user_id ";

    @Override
    public Integer create(SkillSession session) throws SQLException {
        String sql = "INSERT INTO sessions (learner_id, teacher_id, skill_name, hours, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // SYLLABUS: Parameter binding
            ps.setInt(1, session.getLearnerId());
            ps.setInt(2, session.getTeacherId());
            ps.setString(3, session.getSkillName());
            ps.setInt(4, session.getHours());
            ps.setString(5, session.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    @Override
    public Optional<SkillSession> findById(Integer id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "WHERE s.session_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Reads a session INSIDE a transaction and locks the row (FOR UPDATE),
     * so nobody can change its status until we commit or roll back.
     */
    public Optional<SkillSession> findByIdForUpdate(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "WHERE s.session_id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<SkillSession> findAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "ORDER BY s.session_id DESC")) {
            return mapAll(ps);
        }
    }

    /** Every session where the user is the learner OR the teacher (newest first). */
    public List<SkillSession> findByUser(int userId) throws SQLException {
        String sql = SELECT_JOIN + "WHERE s.learner_id = ? OR s.teacher_id = ? ORDER BY s.session_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            return mapAll(ps);
        }
    }

    /** True if the same learner already has an open (PENDING/ACCEPTED) request for this teacher and skill. */
    public boolean existsOpenSession(int learnerId, int teacherId, String skillName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sessions WHERE learner_id = ? AND teacher_id = ? "
                   + "AND skill_name = ? AND status IN ('PENDING', 'ACCEPTED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, learnerId);
            ps.setInt(2, teacherId);
            ps.setString(3, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** GenericDao update:  */
    //saves the status of the session object.
    @Override
    public boolean update(SkillSession session) throws SQLException {
        String sql = "UPDATE sessions SET status = ? WHERE session_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getStatus().name());
            ps.setInt(2, session.getSessionId());
            return ps.executeUpdate() == 1;
        }
    }

    /** Conditional status change in its own connection (accept / reject / cancel). */
    public boolean updateStatus(int sessionId, SessionStatus newStatus, SessionStatus expectedStatus) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, sessionId, newStatus, expectedStatus);
        }
    }

    /** Conditional status change inside the caller's transaction. */
    // True only if exactly 1 row changed.
    public boolean updateStatus(Connection conn, int sessionId, SessionStatus newStatus,
                                SessionStatus expectedStatus) throws SQLException {
        String sql = "UPDATE sessions SET status = ? WHERE session_id = ? AND status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setInt(2, sessionId);
            ps.setString(3, expectedStatus.name());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE session_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private List<SkillSession> mapAll(PreparedStatement ps) throws SQLException {
        List<SkillSession> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    // skill session
    // SYLLABUS: ResultSet mapping with typed getters
    //  (getInt, getString, getTimestamp)
    private SkillSession mapRow(ResultSet rs) throws SQLException {
        Timestamp requested = rs.getTimestamp("requested_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new SkillSession(
                rs.getInt("session_id"),
                rs.getInt("learner_id"),
                rs.getString("learner_name"),
                rs.getInt("teacher_id"),
                rs.getString("teacher_name"),
                rs.getString("skill_name"),
                rs.getInt("hours"),
                SessionStatus.valueOf(rs.getString("status")),
                requested.toLocalDateTime(),
                updated.toLocalDateTime());
    }
}

