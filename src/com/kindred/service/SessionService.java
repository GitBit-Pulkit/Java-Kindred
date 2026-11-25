package com.kindred.service;

import com.kindred.dao.SessionDao;
import com.kindred.dao.UserDao;
import com.kindred.model.SessionStatus;
import com.kindred.model.SkillSession;
import com.kindred.model.User;
import com.kindred.util.ConfigLoader;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * SESSION MANAGEMENT: request -> accept/reject -> complete (or cancel).
 *
 * Who may do what:
 *   learner : request, cancel while PENDING, mark COMPLETED once ACCEPTED (this pays the teacher)
 *   teacher : accept or reject while PENDING
 * Only the learner can complete a session, so a teacher can never pay themselves.
 *
 * Every state change PRODUCES notifications into the background queue
 * (NotificationService), so the menu never waits for them.
 */
public class SessionService {

    private final SessionDao sessionDao = new SessionDao();
    private final UserDao userDao = new UserDao();
    private final SkillService skillService;
    private final CreditService creditService;
    private final NotificationService notifications;

    public SessionService(SkillService skillService, CreditService creditService, NotificationService notifications) {
        this.skillService = skillService;
        this.creditService = creditService;
        this.notifications = notifications;
    }

    public SkillSession requestSession(User learner, String teacherUsername, String skillName, int hours)
            throws ServiceException {
        int maxHours = ConfigLoader.getInt("max.session.hours", 4);
        if (hours < 1 || hours > maxHours) {
            throw new ServiceException("A session must be between 1 and " + maxHours + " hours.");
        }
        try {
            User teacher = userDao.findByUsername(teacherUsername)
                    .orElseThrow(() -> new ServiceException("No user called '" + teacherUsername + "'."));
            if (teacher.getUserId() == learner.getUserId()) {
                throw new ServiceException("You cannot book a session with yourself.");
            }

            // The teacher must really TEACH this skill (uses their spelling, e.g. "java" -> "Java")
            String officialName = skillService.findTeachSkillName(teacher.getUserId(), skillName)
                    .orElseThrow(() -> new ServiceException(teacherUsername + " does not teach '" + skillName + "'."));

            // Early balance check (the real, locked check happens again at completion time)
            int cost = hours * CreditService.creditsPerHour();
            User freshLearner = userDao.findById(learner.getUserId())
                    .orElseThrow(() -> new ServiceException("Your account no longer exists."));
            if (freshLearner.getCredits() < cost) {
                throw new ServiceException("This session costs " + cost + " credits but you only have "
                        + freshLearner.getCredits() + ". Teach someone to earn more!");
            }

            if (sessionDao.existsOpenSession(learner.getUserId(), teacher.getUserId(), officialName)) {
                throw new ServiceException("You already have an open '" + officialName + "' request with " + teacherUsername + ".");
            }

            SkillSession session = new SkillSession(learner.getUserId(), teacher.getUserId(), officialName, hours);
            session.setSessionId(sessionDao.create(session));

            // ---- produce notifications (handled by the background consumer thread)
            notifications.notifyUser(teacher.getUserId(), learner.getUsername() + " requested a " + hours
                    + "h '" + officialName + "' session with you (session #" + session.getSessionId() + ").");
            notifications.notifyUser(learner.getUserId(), "Your request #" + session.getSessionId()
                    + " for '" + officialName + "' was sent to " + teacherUsername + ".");
            return session;

        } catch (SQLException e) {
            throw new ServiceException("Could not create the session: " + e.getMessage(), e);
        }
    }

    /** Teacher accepts (true) or rejects (false) a PENDING request. */
    public void respondToRequest(User teacher, int sessionId, boolean accept) throws ServiceException {
        SkillSession session = load(sessionId);
        if (session.getTeacherId() != teacher.getUserId()) {
            throw new ServiceException("Session #" + sessionId + " is not a request sent to you.");
        }
        SessionStatus newStatus = accept ? SessionStatus.ACCEPTED : SessionStatus.REJECTED;
        changeStatus(session, newStatus, SessionStatus.PENDING);

        String verb = accept ? "ACCEPTED" : "REJECTED";
        notifications.notifyUser(session.getLearnerId(), teacher.getUsername() + " " + verb
                + " your '" + session.getSkillName() + "' session #" + sessionId + ".");
        notifications.notifyUser(teacher.getUserId(), "You " + verb.toLowerCase() + " session #" + sessionId + ".");
    }

    /** Learner withdraws a request that is still PENDING. */
    public void cancelRequest(User learner, int sessionId) throws ServiceException {
        SkillSession session = load(sessionId);
        if (session.getLearnerId() != learner.getUserId()) {
            throw new ServiceException("Session #" + sessionId + " is not your request.");
        }
        changeStatus(session, SessionStatus.CANCELLED, SessionStatus.PENDING);
        notifications.notifyUser(session.getTeacherId(), learner.getUsername() + " cancelled the request for session #" + sessionId + ".");
    }

    /** Learner confirms the lesson happened. Credits move inside a JDBC transaction. */
    public SkillSession completeSession(User learner, int sessionId) throws ServiceException {
        SkillSession done = creditService.completeSession(sessionId, learner.getUserId());
        int cost = done.getHours() * CreditService.creditsPerHour();
        notifications.notifyUser(done.getTeacherId(), "Session #" + sessionId + " ('" + done.getSkillName()
                + "') was completed by " + learner.getUsername() + ". You earned " + cost + " credits!");
        notifications.notifyUser(learner.getUserId(), "Session #" + sessionId + " completed. "
                + cost + " credits paid to " + done.getTeacherUsername() + ".");
        return done;
    }

    // ------------------------------------------------------------ queries

    public List<SkillSession> getHistory(int userId) throws ServiceException {
        try {
            return sessionDao.findByUser(userId);
        } catch (SQLException e) {
            throw new ServiceException("Could not load sessions: " + e.getMessage(), e);
        }
    }

    /** Requests waiting for MY answer as a teacher. */
    public List<SkillSession> getIncomingRequests(int userId) throws ServiceException {
        return filterHistory(userId, s -> s.getTeacherId() == userId && s.getStatus() == SessionStatus.PENDING);
    }

    /** My requests (as learner) still waiting for the teacher. */
    public List<SkillSession> getMyPendingRequests(int userId) throws ServiceException {
        return filterHistory(userId, s -> s.getLearnerId() == userId && s.getStatus() == SessionStatus.PENDING);
    }

    /** ACCEPTED sessions where I am the learner (these can be marked completed). */
    public List<SkillSession> getSessionsToComplete(int userId) throws ServiceException {
        return filterHistory(userId, s -> s.getLearnerId() == userId && s.getStatus() == SessionStatus.ACCEPTED);
    }

    /** Re-usable filter: the condition is passed in as a lambda. */
    // SYLLABUS: Lambdas for filtering - Predicate passed into a Stream filter()
    private List<SkillSession> filterHistory(int userId, Predicate<SkillSession> condition) throws ServiceException {
        return getHistory(userId).stream()
                .filter(condition)
                .collect(Collectors.toList());
    }

    private SkillSession load(int sessionId) throws ServiceException {
        try {
            Optional<SkillSession> s = sessionDao.findById(sessionId);
            return s.orElseThrow(() -> new ServiceException("Session #" + sessionId + " does not exist."));
        } catch (SQLException e) {
            throw new ServiceException("Could not load session: " + e.getMessage(), e);
        }
    }

    private void changeStatus(SkillSession session, SessionStatus newStatus, SessionStatus expected) throws ServiceException {
        if (session.getStatus() != expected) {
            throw new ServiceException("Session #" + session.getSessionId() + " is " + session.getStatus()
                    + " (must be " + expected + ").");
        }
        try {
            if (!sessionDao.updateStatus(session.getSessionId(), newStatus, expected)) {
                throw new ServiceException("Session #" + session.getSessionId() + " was changed meanwhile. Try again.");
            }
            session.setStatus(newStatus);
        } catch (SQLException e) {
            throw new ServiceException("Could not update session: " + e.getMessage(), e);
        }
    }
}
