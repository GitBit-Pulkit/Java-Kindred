package com.kindred.model;

import java.time.LocalDateTime;


/**
 * A learning session between a LEARNER (pays credits) and a TEACHER (earns credits).
 * Maps to one row of the `sessions` table.
 *
 * Cost of a session = hours x credits.per.hour (from config/kindred.conf).
 * The usernames are not columns of `sessions`. They come from JOINs with `users`.
 */


// SYLLABUS: CRUD Operations - entity created, read and updated by SessionDao (ResultSet mapping)
public class SkillSession implements CsvExportable {

    public static final String CSV_HEADER =
            "session_id,learner,teacher,skill,hours,status,requested_at,updated_at";

    private int sessionId;
    private int learnerId;
    private String learnerUsername;
    private int teacherId;
    private String teacherUsername;
    private String skillName;
    private int hours;
    private SessionStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;

    /** A new request (status PENDING) that is not saved yet. */
    public SkillSession(int learnerId, int teacherId, String skillName, int hours) {
        this.learnerId = learnerId;
        this.teacherId = teacherId;
        this.skillName = skillName;
        this.hours = hours;
        this.status = SessionStatus.PENDING;
    }

    /** A session loaded from the database. */
    public SkillSession(int sessionId, int learnerId, String learnerUsername, int teacherId,
                        String teacherUsername, String skillName, int hours, SessionStatus status,
                        LocalDateTime requestedAt, LocalDateTime updatedAt) {
        this(learnerId, teacherId, skillName, hours);
        this.sessionId = sessionId;
        this.learnerUsername = learnerUsername;
        this.teacherUsername = teacherUsername;
        this.status = status;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
    }

    public int getSessionId()           { return sessionId; }
    public int getLearnerId()           { return learnerId; }
    public String getLearnerUsername()  { return learnerUsername; }
    public int getTeacherId()           { return teacherId; }
    public String getTeacherUsername()  { return teacherUsername; }
    public String getSkillName()        { return skillName; }
    public int getHours()               { return hours; }
    public SessionStatus getStatus()    { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getUpdatedAt()   { return updatedAt; }

    public void setSessionId(int sessionId)      { this.sessionId = sessionId; }
    public void setStatus(SessionStatus status)  { this.status = status; }

    @Override
    public String[] csvValues() {
        return new String[] {
                String.valueOf(sessionId), learnerUsername, teacherUsername, skillName,
                String.valueOf(hours), status.name(),
                String.valueOf(requestedAt), String.valueOf(updatedAt)
        };
    }

    @Override
    public String toString() {
        return String.format("Session #%-4d %-18s learner: %-12s teacher: %-12s %dh  %s",
                sessionId, skillName, learnerUsername, teacherUsername, hours, status);
    }
}

//`SkillSession.java` defines a Java data model in `com.kindred.model` 
// that represents a tutoring session between a learner and a teacher, 
// using `java.time.LocalDateTime` for timestamps alongside internal types `SessionStatus` and `CsvExportable`. 
// It encapsulates database row attributes from the `sessions` table—along with joined usernames from the `users` table—via two constructors for either initializing a new `PENDING` session request or hydrating an existing record from `SessionDao`. 
// The class exposes read access to all fields while restricting updates strictly to `sessionId` and `status`, and provides `csvValues()` and `toString()` methods for CSV exporting and formatted console display.
