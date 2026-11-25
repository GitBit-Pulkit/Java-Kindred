package com.kindred.service;

import com.kindred.dao.SessionDao;
import com.kindred.dao.TransactionDao;
import com.kindred.dao.UserDao;
import com.kindred.model.CreditTransaction;
import com.kindred.model.SkillSession;
import com.kindred.model.User;
import com.kindred.util.CollectionUtils;

import java.sql.SQLException;
import java.util.List;

/**
 * ROLE-BASED features: only an ADMIN may call these.
 * Every method re-checks the role, so a normal user cannot reach admin data
 * even if the menu were bypassed.
 */
public class AdminService {

    private final UserDao userDao = new UserDao();
    private final TransactionDao transactionDao = new TransactionDao();
    private final SessionDao sessionDao = new SessionDao();

    public List<User> getAllUsers(User admin) throws ServiceException {
        requireAdmin(admin);
        try {
            return userDao.findAll();
        } catch (SQLException e) {
            throw new ServiceException("Could not load users: " + e.getMessage(), e);
        }
    }

    /** Users with the highest balances (uses the generic bounded method topN). */
    // SYLLABUS: Generics - calls bounded generic method CollectionUtils.topN with T = User
    public List<User> getLeaderboard(User admin, int n) throws ServiceException {
        return CollectionUtils.topN(getAllUsers(admin), n);
    }

    public List<CreditTransaction> getAllTransactions(User admin) throws ServiceException {
        requireAdmin(admin);
        try {
            return transactionDao.findAll();
        } catch (SQLException e) {
            throw new ServiceException("Could not load transactions: " + e.getMessage(), e);
        }
    }

    public List<SkillSession> getAllSessions(User admin) throws ServiceException {
        requireAdmin(admin);
        try {
            return sessionDao.findAll();
        } catch (SQLException e) {
            throw new ServiceException("Could not load sessions: " + e.getMessage(), e);
        }
    }

    /** CRUD Delete. ON DELETE CASCADE removes the user's skills, sessions and notifications. */
    // SYLLABUS: CRUD Operations - Delete (UserDao.delete -> executeUpdate)
    public void deleteUser(User admin, int userId) throws ServiceException {
        requireAdmin(admin);
        if (userId == admin.getUserId()) {
            throw new ServiceException("An admin cannot delete their own account.");
        }
        try {
            if (!userDao.delete(userId)) {
                throw new ServiceException("No user with id " + userId + ".");
            }
        } catch (SQLException e) {
            throw new ServiceException("Could not delete user: " + e.getMessage(), e);
        }
    }

    private void requireAdmin(User user) throws ServiceException {
        if (user == null || !user.isAdmin()) {
            throw new ServiceException("Access denied: admin only.");
        }
    }
}
