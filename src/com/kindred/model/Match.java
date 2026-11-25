package com.kindred.model;

import java.util.List;

/**
 * Result produced by the SmartMatch engine for ONE other user.
 * It is calculated in memory and NOT stored in the database.
 *
 * theyCanTeachMe -> skills this user TEACHES that I want to LEARN
 * theyWantFromMe -> skills this user wants to LEARN that I TEACH
 * score          -> compatibility percentage 0..100 (see CompatibilityScorer)
 */
// SYLLABUS: Stream API - produced by the SmartMatch stream pipeline; isMutual is used as a method-reference Predicate
public class Match {

    private final int userId;
    private final String username;
    private final List<String> theyCanTeachMe;
    private final List<String> theyWantFromMe;
    private final double score;

    public Match(int userId, String username, List<String> theyCanTeachMe,
                 List<String> theyWantFromMe, double score) {
        this.userId = userId;
        this.username = username;
        this.theyCanTeachMe = theyCanTeachMe;
        this.theyWantFromMe = theyWantFromMe;
        this.score = score;
    }

    public int getUserId()                { return userId; }
    public String getUsername()           { return username; }
    public List<String> getTheyCanTeachMe() { return theyCanTeachMe; }
    public List<String> getTheyWantFromMe() { return theyWantFromMe; }
    public double getScore()              { return score; }

    /** A "mutual" match = a true two-way swap: both sides have something for the other. */
    public boolean isMutual() {
        return !theyCanTeachMe.isEmpty() && !theyWantFromMe.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%-15s %5.1f%% %s%n      can teach you : %s%n      wants from you: %s",
                username, score, isMutual() ? "[MUTUAL SWAP]" : "",
                theyCanTeachMe.isEmpty() ? "-" : String.join(", ", theyCanTeachMe),
                theyWantFromMe.isEmpty() ? "-" : String.join(", ", theyWantFromMe));
    }
}
