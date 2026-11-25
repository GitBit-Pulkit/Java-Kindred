package com.kindred.service;

import com.kindred.dao.SessionDao;
import com.kindred.dao.TransactionDao;
import com.kindred.dao.UserDao;
import com.kindred.db.DBConnection;
import com.kindred.model.CreditTransaction;
import com.kindred.model.SessionStatus;
import com.kindred.model.SkillSession;
import com.kindred.model.TransactionType;
import com.kindred.util.CollectionUtils;
import com.kindred.util.ConfigLoader;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CREDIT ECONOMY: the wallet and the ATOMIC credit transfer.
 *
 * When a learner marks a session COMPLETED, FIVE database changes must happen
 * together:
 *   1. lock and re-check the session (must still be ACCEPTED)
 *   2. lock and check the learner's balance
 *   3. learner.credits -= cost
 *   4. teacher.credits += cost
 *   5. insert a SESSION_PAYMENT ledger row and set the session to COMPLETED
 *
 * They run in ONE JDBC transaction. If ANY step fails (not enough credits,
 * session already completed, database error, crash...) we ROLLBACK and none of
 * the changes are saved. Credits can never disappear or be created out of thin air.
 */
public class CreditService {

    private final UserDao userDao = new UserDao();
    private final SessionDao sessionDao = new SessionDao();
    private final TransactionDao transactionDao = new TransactionDao();

    public static int creditsPerHour() {
        return ConfigLoader.getInt("credits.per.hour", 1);
    }

    /**
     * Completes a session and transfers its cost from learner to teacher, atomically.
     * @return the completed session (so the caller can notify both users)
     */
    public SkillSession completeSession(int sessionId, int learnerId) throws ServiceException {
        try (Connection conn = DBConnection.getConnection()) {   // SYLLABUS: try-with-resources

            conn.setAutoCommit(false);   // SYLLABUS: JDBC Transactions - setAutoCommit(false) starts the transaction
            try {
                // 1. Lock the session row and validate it
                Optional<SkillSession> found = sessionDao.findByIdForUpdate(conn, sessionId);
                if (found.isEmpty()) {
                    throw new ServiceException("Session #" + sessionId + " does not exist.");
                }
                SkillSession session = found.get();
                if (session.getLearnerId() != learnerId) {
                    throw new ServiceException("Only the learner of session #" + sessionId + " can mark it completed.");
                }
                if (session.getStatus() != SessionStatus.ACCEPTED) {
                    throw new ServiceException("Session #" + sessionId + " is " + session.getStatus()
                            + ". Only ACCEPTED sessions can be completed.");
                }

                int cost = session.getHours() * creditsPerHour();

                // 2. Lock the learner's wallet and check the balance
                int balance = userDao.getCreditsForUpdate(conn, learnerId);
                if (balance < cost) {
                    throw new ServiceException("Not enough credits: this session costs " + cost
                            + " but you have " + balance + ". Teach a session to earn more!");
                }

                // 3 + 4. Move the credits
                userDao.adjustCredits(conn, learnerId, -cost);
                userDao.adjustCredits(conn, session.getTeacherId(), +cost);

                // 5. Ledger row + status change (conditional: must still be ACCEPTED)
                transactionDao.insert(conn, new CreditTransaction(
                        sessionId, learnerId, session.getTeacherId(), cost, TransactionType.SESSION_PAYMENT));
                if (!sessionDao.updateStatus(conn, sessionId, SessionStatus.COMPLETED, SessionStatus.ACCEPTED)) {
                    throw new ServiceException("Session #" + sessionId + " was changed by someone else. Try again.");
                }

                conn.commit();   // SYLLABUS: JDBC Transactions - commit() saves ALL changes together
                session.setStatus(SessionStatus.COMPLETED);
                return session;

            } catch (SQLException | ServiceException e) {
                conn.rollback(); // SYLLABUS: JDBC Transactions - rollback() undoes EVERY change since setAutoCommit(false)
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ServiceException("Credit transfer failed and was rolled back: " + e.getMessage(), e);
        }
    }

    /** The user's ledger entries, newest first. */
    public List<CreditTransaction> getHistory(int userId) throws ServiceException {
        try {
            return transactionDao.findByUser(userId);
        } catch (SQLException e) {
            throw new ServiceException("Could not load transactions: " + e.getMessage(), e);
        }
    }

    /** Total credits ever RECEIVED by the user (bonus + teaching). */
    // SYLLABUS: Stream filter() + map() feeding a generic List<? extends Number> method
    public int totalEarned(List<CreditTransaction> history, int userId) {
        List<Integer> amounts = history.stream()
                .filter(t -> t.getToUserId() != null && t.getToUserId() == userId)
                .map(CreditTransaction::getAmount)
                .collect(Collectors.toList());
        return CollectionUtils.sum(amounts);
    }

    /** Total credits ever SPENT by the user on learning. */
    public int totalSpent(List<CreditTransaction> history, int userId) {
        List<Integer> amounts = history.stream()
                .filter(t -> t.getFromUserId() != null && t.getFromUserId() == userId)
                .map(CreditTransaction::getAmount)
                .collect(Collectors.toList());
        return CollectionUtils.sum(amounts);
    }
}
