package com.kindred.service;

import com.kindred.dao.SkillDao;
import com.kindred.model.Match;
import com.kindred.model.Skill;
import com.kindred.model.SkillType;
import com.kindred.model.User;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * SMARTMATCH ENGINE: finds and ranks the users most compatible with me.
 *
 * Steps:
 *  1. Load MY skills from MySQL and split them into myTeach / myLearn name sets.
 *  2. Load EVERY OTHER user's skills (one query) and group them by user.
 *  3. For each other user:
 *       theyCanTeachMe = their TEACH skills  ∩ my LEARN skills
 *       theyWantFromMe = their LEARN skills  ∩ my TEACH skills
 *       score          = CompatibilityScorer (a lambda)
 *  4. Drop users with score 0, sort by score (high to low, then by name), keep the top N.
 *
 * Everything after the database read is a Stream pipeline. Streams are LAZY:
 * filter / map / sorted only describe the work, and nothing runs until the
 * terminal operation collect() is called.
 */
public class MatchService {

    private final SkillDao skillDao = new SkillDao();
    private final CompatibilityScorer scorer;

    public MatchService() {
        this(CompatibilityScorer.BALANCED);
    }

    /** Any other scoring rule can be passed in as a lambda. */
    public MatchService(CompatibilityScorer scorer) {
        this.scorer = scorer;
    }

    public List<Match> findMatches(User me, int limit) throws ServiceException {
        if (me == null) {
        throw new ServiceException("User cannot be null.");
    }

    if (limit <= 0) {
        throw new ServiceException("Match limit must be greater than zero.");
    }
        try {
            List<Skill> mySkills = skillDao.findByUser(me.getUserId());
            Set<String> myTeach = namesOfType(mySkills, SkillType.TEACH);
            Set<String> myLearn = namesOfType(mySkills, SkillType.LEARN);

            if (myTeach.isEmpty() && myLearn.isEmpty()) {
                throw new ServiceException("Your Skill DNA is empty. Add skills you TEACH and want to LEARN first.");
            }

            List<Skill> othersSkills = skillDao.findAllExceptUser(me.getUserId());

            // SYLLABUS: Stream collect(groupingBy) with a method reference -> Map<userId, their skills>
            Map<Integer, List<Skill>> skillsByUser = othersSkills.stream()
                    .collect(Collectors.groupingBy(Skill::getUserId));

            // SYLLABUS: Stream pipeline - map() -> filter() -> sorted() -> limit() -> collect()
            // SYLLABUS: Lazy evaluation - nothing executes until collect()
            return skillsByUser.values().stream()
                    .map(theirSkills -> buildMatch(theirSkills, myTeach, myLearn))  // lambda
                    .filter(match -> match.getScore() > 0)                          // lambda
                    .sorted(Comparator.comparingDouble(Match::getScore).reversed()  // method references
                            .thenComparing(Match::getUsername))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (SQLException e) {
            throw new ServiceException("SmartMatch could not read skills: " + e.getMessage(), e);
        }
    }

    /**
     * Extra filtering chosen by the user, e.g. matches -> only mutual swaps:
     *     filter(matches, Match::isMutual)
     * Predicate<? super Match> accepts a Predicate<Match> or a Predicate<Object>.
     */
    // SYLLABUS: Lambdas for filtering - java.util.function.Predicate
    // SYLLABUS: Generics - lower bounded wildcard Predicate<? super Match>
    public List<Match> filter(List<Match> matches, Predicate<? super Match> condition) {
        return matches.stream()
                .filter(condition)
                .collect(Collectors.toList());
    }

    /** Lower-case names of one skill type, as a Set (no duplicates, fast contains()). */
    // SYLLABUS: Stream filter() + map() with method reference
    private Set<String> namesOfType(List<Skill> skills, SkillType type) {
        return skills.stream()
                .filter(s -> s.getType() == type)
                .map(Skill::normalizedName)
                .collect(Collectors.toSet());
    }

    /** Builds the Match for ONE other user. */
    // SYLLABUS: Stream filter() + map() + distinct() + sorted()
    private Match buildMatch(List<Skill> theirSkills, Set<String> myTeach, Set<String> myLearn) {
        Skill any = theirSkills.get(0); // every skill in this list has the same owner

        List<String> theyCanTeachMe = theirSkills.stream()
                .filter(s -> s.getType() == SkillType.TEACH)
                .filter(s -> myLearn.contains(s.normalizedName()))
                .map(Skill::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<String> theyWantFromMe = theirSkills.stream()
                .filter(s -> s.getType() == SkillType.LEARN)
                .filter(s -> myTeach.contains(s.normalizedName()))
                .map(Skill::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double score = scorer.score(theyCanTeachMe.size(), myLearn.size(),
                                    theyWantFromMe.size(), myTeach.size());

        return new Match(any.getUserId(), any.getOwnerUsername(), theyCanTeachMe, theyWantFromMe, score);
    }
}
