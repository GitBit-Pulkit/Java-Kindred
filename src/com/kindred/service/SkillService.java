package com.kindred.service;

import com.kindred.dao.SkillDao;
import com.kindred.model.Skill;
import com.kindred.model.SkillType;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Skill DNA management: add, view, update and delete the skills a user
 * TEACHES and wants to LEARN. Every change is saved in MySQL through SkillDao.
 */
public class SkillService {

    private final SkillDao skillDao = new SkillDao();

    /** All skills of a user: TEACH first, then LEARN, each alphabetical. */
    // SYLLABUS: Stream sorted() with Comparator built from method references
    public List<Skill> getSkills(int userId) throws ServiceException {
        try {
            return skillDao.findByUser(userId).stream()
                    .sorted(Comparator.comparing(Skill::getType)
                            .thenComparing(Skill::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Could not load skills: " + e.getMessage(), e);
        }
    }

    /** Only the skills of one type. */
    // SYLLABUS: Stream filter() with a lambda
    public List<Skill> getSkills(int userId, SkillType type) throws ServiceException {
        if (userId <= 0) {
        throw new ServiceException("Invalid user ID.");
    }

    if (type == null) {
        throw new ServiceException("Skill type cannot be null.");
    }

        return getSkills(userId).stream()
                .filter(skill -> skill.getType() == type)
                .collect(Collectors.toList());
    }

    public Skill addSkill(int userId, String name, SkillType type, int proficiency) throws ServiceException {
        String cleanName = cleanName(name);
        validateProficiency(proficiency);
        Skill skill = new Skill(userId, cleanName, type, proficiency);
        try {
            skill.setSkillId(skillDao.create(skill));
            return skill;
        } catch (SQLIntegrityConstraintViolationException e) {
            // UNIQUE (user_id, skill_name, skill_type) in MySQL caught a duplicate
            throw new ServiceException("'" + cleanName + "' is already in your " + type + " list.");
        } catch (SQLException e) {
            throw new ServiceException("Could not add skill: " + e.getMessage(), e);
        }
    }

    /** Changes name and/or proficiency of one of the user's OWN skills. */
    public void updateSkill(int userId, int skillId, String newName, int newProficiency) throws ServiceException {
        Skill skill = getOwnSkill(userId, skillId);
        if (newName != null && !newName.isBlank()) {
            skill.setName(cleanName(newName));
        }
        validateProficiency(newProficiency);
        skill.setProficiency(newProficiency);
        try {
            skillDao.update(skill);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ServiceException("You already have a " + skill.getType() + " skill called '" + skill.getName() + "'.");
        } catch (SQLException e) {
            throw new ServiceException("Could not update skill: " + e.getMessage(), e);
        }
    }

    public void deleteSkill(int userId, int skillId) throws ServiceException {
        getOwnSkill(userId, skillId);   // ownership check
        try {
            skillDao.delete(skillId);
        } catch (SQLException e) {
            throw new ServiceException("Could not delete skill: " + e.getMessage(), e);
        }
    }

    /**
     * Saves skills read from a CSV file. Duplicates and invalid entries are
     * skipped with a message.
     * @return how many were actually added
     */
    public int addImportedSkills(int userId, List<Skill> skills) {
        int added = 0;
        for (Skill s : skills) {
            try {
                addSkill(userId, s.getName(), s.getType(), s.getProficiency());
                added++;
            } catch (ServiceException e) {
                System.out.println("  [Import] Skipped '" + s.getName() + "': " + e.getMessage());
            }
        }
        return added;
    }

    /**
     * Finds the teacher's own spelling of a TEACH skill (case-insensitive),
     * e.g. user typed "java" -> teacher listed "Java".
     */
    // SYLLABUS: Stream filter() + map() + findFirst() returning Optional
    public Optional<String> findTeachSkillName(int teacherId, String requestedName) throws ServiceException {
        String wanted = requestedName.trim().toLowerCase();
        return getSkills(teacherId, SkillType.TEACH).stream()
                .filter(skill -> skill.normalizedName().equals(wanted))
                .map(Skill::getName)
                .findFirst();
    }

    private Skill getOwnSkill(int userId, int skillId) throws ServiceException {
        try {
            Optional<Skill> skill = skillDao.findById(skillId);
            if (skill.isEmpty() || skill.get().getUserId() != userId) {
                throw new ServiceException("Skill #" + skillId + " is not in your Skill DNA.");
            }
            return skill.get();
        } catch (SQLException e) {
            throw new ServiceException("Could not load skill: " + e.getMessage(), e);
        }
    }

    /** Trims, collapses repeated spaces and checks allowed characters. */
    private String cleanName(String name) throws ServiceException {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > 40) {
            throw new ServiceException("Skill name must be 1-40 characters.");
        }
        if (!clean.matches("[A-Za-z0-9+#.\\- ]+")) {
            throw new ServiceException("Skill name may only contain letters, digits, spaces and + # . -");
        }
        return clean;
    }

    private void validateProficiency(int proficiency) throws ServiceException {
        if (proficiency < 1 || proficiency > 5) {
            throw new ServiceException("Proficiency must be between 1 and 5.");
        }
    }
}
