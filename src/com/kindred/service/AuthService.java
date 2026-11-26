package com.kindred.service;

import com.kindred.dao.TransactionDao;
import com.kindred.dao.UserDao;
import com.kindred.db.DBConnection;
import com.kindred.model.CreditTransaction;
import com.kindred.model.Role;
import com.kindred.model.User;
import com.kindred.util.ConfigLoader;
import com.kindred.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/**
 * Registration, login and account updates.
 *
 * Registration is a JDBC TRANSACTION of two INSERTs:
 *   1) the user row (with the starting credits), and
 *   2) a WELCOME_BONUS row in the ledger.
 * Either both are saved or neither, so the ledger always explains every credit.
 */
public class AuthService {

    private final UserDao userDao = new UserDao();
    private final TransactionDao transactionDao = new TransactionDao();

    public User register(String username, String fullName, String password) throws ServiceException {
        // ---- validation (before touching the database)
        if (!username.matches("[A-Za-z0-9_]{3,20}")) {
            throw new ServiceException("Username must be 3-20 characters: letters, digits or _ only.");
        }
        if (fullName.isBlank() || fullName.length() > 60) {
            throw new ServiceException("Full name must be 1-60 characters.");
        }
        validatePassword(password);

        int startingCredits = ConfigLoader.getInt("starting.credits", 5);
        String hash = PasswordUtil.hash(password);   // never store the plain password

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // SYLLABUS: JDBC Transactions - setAutoCommit(false)
            try {
                if (userDao.findByUsername(conn, username).isPresent()) {
                    throw new ServiceException("Username '" + username + "' is already taken.");
                }
                // Role-based logic: the very first account becomes the ADMIN.
                Role role = userDao.countUsers(conn) == 0 ? Role.ADMIN : Role.USER;

                User user = new User(username, fullName.trim(), hash, role, startingCredits);
                int newId = userDao.create(conn, user);

                if (startingCredits > 0) {
                    transactionDao.insert(conn, CreditTransaction.welcomeBonus(newId, startingCredits));
                }
                conn.commit(); // SYLLABUS: JDBC Transactions - commit()
                user.setUserId(newId);
                return user;
            } catch (SQLException | ServiceException e) {
                conn.rollback(); // SYLLABUS: JDBC Transactions - rollback()
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // UNIQUE(username) protects us even if two people register the same name at once
            throw new ServiceException("Username '" + username + "' is already taken.");
        } catch (SQLException e) {
            throw new ServiceException("Database error during registration: " + e.getMessage(), e);
        }
    }

    public User login(String username, String password) throws ServiceException {
                if (username == null || username.isBlank()) {
            throw new ServiceException("Username cannot be empty.");
        }
        if (password == null || password.isBlank()) {
            throw new ServiceException("Password cannot be empty.");
        }

        try {
            Optional<User> found = userDao.findByUsername(username);
            // Same message for "no such user" and "wrong password", so nobody can tell which usernames exist.
            if (found.isPresent() && PasswordUtil.verify(password, found.get().getPasswordHash())) {
                return found.get();
            }
            throw new ServiceException("Invalid username or password.");
        } catch (SQLException e) {
            throw new ServiceException("Database error during login: " + e.getMessage(), e);
        }
    }

    /** Re-reads the user from MySQL (fresh balance and role). Empty if the account was deleted. */
    public Optional<User> refresh(User user) throws ServiceException {
        try {
            return userDao.findById(user.getUserId());
        } catch (SQLException e) {
            throw new ServiceException("Could not reload your account: " + e.getMessage(), e);
        }
    }

    public void changeFullName(User user, String newName) throws ServiceException {
        if (newName.isBlank() || newName.length() > 60) {
            throw new ServiceException("Full name must be 1-60 characters.");
        }
        user.setFullName(newName.trim());
        save(user);
    }

    public void changePassword(User user, String oldPassword, String newPassword) throws ServiceException {
        if (!PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            throw new ServiceException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        user.setPasswordHash(PasswordUtil.hash(newPassword));
        save(user);
    }

    private void save(User user) throws ServiceException {
        try {
            if (!userDao.update(user)) {
                throw new ServiceException("Account not found.");
            }
        } catch (SQLException e) {
            throw new ServiceException("Could not update account: " + e.getMessage(), e);
        }
    }

    private void validatePassword(String password) throws ServiceException {
        if (password.length() < 6 || password.length() > 64) {
            throw new ServiceException("Password must be 6-64 characters long.");
        }
    }
}
