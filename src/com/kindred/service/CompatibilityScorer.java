package com.kindred.service;

/**
 * CUSTOM FUNCTIONAL INTERFACE: exactly ONE abstract method, so any scoring rule
 * can be written as a LAMBDA expression and plugged into MatchService.
 *
 * Parameters:
 *   theyCanTeachMe -> how many of MY learn-skills the other user teaches
 *   iWantToLearn   -> how many skills I want to learn in total
 *   theyWantFromMe -> how many of MY teach-skills the other user wants
 *   iCanTeach      -> how many skills I can teach in total
 * Returns a compatibility score.
 */
// SYLLABUS: Custom @FunctionalInterface
@FunctionalInterface
public interface CompatibilityScorer {

    double score(int theyCanTeachMe, int iWantToLearn, int theyWantFromMe, int iCanTeach);

    /**
     * Default rule, written as a LAMBDA:
     *   50% weight -> what fraction of my learning wishes they can teach
     *   50% weight -> what fraction of my teachable skills they want
     * So a perfect two-way swap scores 100%, and a one-way match scores at most 50%.
     */
    // SYLLABUS: Lambda expression implementing the custom functional interface
    CompatibilityScorer BALANCED = (theyCanTeachMe, iWantToLearn, theyWantFromMe, iCanTeach) -> {
        double learnPart = iWantToLearn == 0 ? 0 : 50.0 * theyCanTeachMe / iWantToLearn;
        double teachPart = iCanTeach == 0 ? 0 : 50.0 * theyWantFromMe / iCanTeach;
        return Math.round((learnPart + teachPart) * 10) / 10.0;  // one decimal place
    };
}
