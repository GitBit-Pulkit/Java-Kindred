package com.kindred.dao;

import com.kindred.db.DBConnection;
import com.kindred.model.CreditTransaction;
import com.kindred.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the `transactions` table (the credit LEDGER).
 *
 * It deliberately does NOT implement GenericDao: a ledger is append-only,
 * so there is no update() or delete(). Rows are only inserted (always inside
 * a JDBC transaction) and read.
 */
public class TransactionDao {

    private static final String SELECT_JOIN =
            "SELECT t.txn_id, t.session_id, t.from_user_id, f.username AS from_name, "
          + "t.to_user_id, r.username AS to_name, t.amount, t.txn_type, t.created_at "
          + "FROM transactions t "
          + "LEFT JOIN users f ON t.from_user_id = f.user_id "   // LEFT JOIN: from_user_id may be NULL
          + "LEFT JOIN users r ON t.to_user_id = r.user_id ";

    /** INSERT inside the caller's transaction (registration bonus or session payment). */
    public void insert(Connection conn, CreditTransaction txn) throws SQLException {
        String sql = "INSERT INTO transactions (session_id, from_user_id, to_user_id, amount, txn_type) "
                   + "VALUES (?, ?, ?, ?, ?)";
        // SYLLABUS: PreparedStatement - parameter binding, including NULL values
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, txn.getSessionId());
            setNullableInt(ps, 2, txn.getFromUserId());
            setNullableInt(ps, 3, txn.getToUserId());
            ps.setInt(4, txn.getAmount());
            ps.setString(5, txn.getType().name());
            ps.executeUpdate(); // SYLLABUS: executeUpdate()
        }
    }

    /** All ledger rows where the user sent or received credits (newest first). */
    public List<CreditTransaction> findByUser(int userId) throws SQLException {
        String sql = SELECT_JOIN + "WHERE t.from_user_id = ? OR t.to_user_id = ? ORDER BY t.txn_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            return mapAll(ps);
        }
    }

    /** Whole ledger (admin only). */
    public List<CreditTransaction> findAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + "ORDER BY t.txn_id DESC")) {
            return mapAll(ps);
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** rs.getInt() returns 0 for SQL NULL. wasNull() tells us it was really NULL. */
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private List<CreditTransaction> mapAll(PreparedStatement ps) throws SQLException {
        List<CreditTransaction> list = new ArrayList<>();
        // SYLLABUS: executeQuery() + ResultSet iteration
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CreditTransaction(
                        rs.getInt("txn_id"),
                        getNullableInt(rs, "session_id"),
                        getNullableInt(rs, "from_user_id"),
                        rs.getString("from_name"),
                        getNullableInt(rs, "to_user_id"),
                        rs.getString("to_name"),
                        rs.getInt("amount"),
                        TransactionType.valueOf(rs.getString("txn_type")),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }
        return list;
    }
}
