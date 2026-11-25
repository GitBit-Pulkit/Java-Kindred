package com.kindred.dao;

import com.kindred.db.DBConnection;
import com.kindred.model.Skill;
import com.kindred.model.SkillType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the `skills` table (the "Skill DNA").
 * Every query JOINs `users` so each Skill also knows its owner's username.
 */
// SYLLABUS: CRUD Operations - implements GenericDao<Skill, Integer>
public class SkillDao implements GenericDao<Skill, Integer> {

    private static final String SELECT_JOIN =
            "SELECT s.skill_id, s.user_id, u.username, s.skill_name, s.skill_type, s.proficiency "
          + "FROM skills s JOIN users u ON s.user_id = u.user_id ";

    @Override
    public Integer create(Skill skill) throws SQLException {
        String sql = "INSERT INTO skills (user_id, skill_name, skill_type, proficiency) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             // SYLLABUS: PreparedStatement with parameter binding
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, skill.getUserId());
            ps.setString(2, skill.getName());
            ps.setString(3, skill.getType().name());
            ps.setInt(4, skill.getProficiency());
            ps.executeUpdate(); // SYLLABUS: executeUpdate() - INSERT
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    @Override
    public Optional<Skill> findById(Integer id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "WHERE s.skill_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { // SYLLABUS: executeQuery()
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Skill> findAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "ORDER BY s.user_id, s.skill_type, s.skill_name")) {
            return mapAll(ps);
        }
    }

    /** All skills of ONE user. */
    public List<Skill> findByUser(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "WHERE s.user_id = ? ORDER BY s.skill_type, s.skill_name")) {
            ps.setInt(1, userId);
            return mapAll(ps);
        }
    }

    /** Skills of EVERY OTHER user. This is the raw input of the SmartMatch engine. */
    public List<Skill> findAllExceptUser(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "WHERE s.user_id <> ?")) {
            ps.setInt(1, userId);
            return mapAll(ps);
        }
    }

    /** Does this user have this skill with this type? (MySQL comparison is case-insensitive.) */
    public boolean userHasSkill(int userId, String skillName, SkillType type) throws SQLException {
        String sql = "SELECT COUNT(*) FROM skills WHERE user_id = ? AND skill_name = ? AND skill_type = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, skillName.trim());
            ps.setString(3, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Updates the name and proficiency of a skill. */
    @Override
    public boolean update(Skill skill) throws SQLException {
        String sql = "UPDATE skills SET skill_name = ?, proficiency = ? WHERE skill_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getName());
            ps.setInt(2, skill.getProficiency());
            ps.setInt(3, skill.getSkillId());
            return ps.executeUpdate() == 1; // SYLLABUS: executeUpdate() - UPDATE
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM skills WHERE skill_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1; // SYLLABUS: executeUpdate() - DELETE
        }
    }

    private List<Skill> mapAll(PreparedStatement ps) throws SQLException {
        List<Skill> skills = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skills.add(mapRow(rs));
            }
        }
        return skills;
    }

    // SYLLABUS: ResultSet mapping with typed getters
    private Skill mapRow(ResultSet rs) throws SQLException {
        return new Skill(
                rs.getInt("skill_id"),
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("skill_name"),
                SkillType.valueOf(rs.getString("skill_type")),
                rs.getInt("proficiency"));
    }
}
