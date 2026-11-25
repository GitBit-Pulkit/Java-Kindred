package com.kindred.db;

import com.kindred.util.ConfigLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The ONE place where database connections are created.
 *
 * Connection settings come from config/kindred.conf (read by ConfigLoader).
 * Each call returns a NEW Connection. Callers must close it, which they do
 * with try-with-resources, so connections are never leaked.
 *
 * Every thread uses its own Connection (the notification thread gets its own),
 * because a JDBC Connection should not be shared between threads.
 */
public final class DBConnection {

    private static String url;
    private static String user;
    private static String password;

    private DBConnection() { }

    /** Reads the DB settings. Must be called once after ConfigLoader.load(). */
    public static void init() {
        url = ConfigLoader.getString("db.url",
                "jdbc:mysql://localhost:3306/kindred?createDatabaseIfNotExist=true");
        user = ConfigLoader.getString("db.user", "root");
        password = ConfigLoader.getString("db.password", "");
    }

    // SYLLABUS: DriverManager - opens a Connection using the JDBC URL.
    // mysql-connector-j (in lib/) registers itself automatically (JDBC 4 service loading),
    // so Class.forName("com.mysql.cj.jdbc.Driver") is not needed.
    public static Connection getConnection() throws SQLException {
        if (url == null) {
            init();
        }
        return DriverManager.getConnection(url, user, password);
    }

    /** Opens and closes one connection to check that MySQL is reachable. */
    public static boolean testConnection() {
        // SYLLABUS: try-with-resources - the Connection is closed automatically
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            System.out.println("[DB] Cannot connect to MySQL: " + e.getMessage());
            return false;
        }
    }
}
