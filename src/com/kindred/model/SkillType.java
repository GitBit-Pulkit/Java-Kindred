package com.kindred.model;

/**
 * Every skill in a user's "Skill DNA" is either one they can TEACH
 * or one they want to LEARN.
 * Stored in MySQL as ENUM('TEACH','LEARN').
 */
// SYLLABUS: Lambda filtering - SmartMatch filters skills with  s -> s.getType() == SkillType.TEACH
public enum SkillType {
    TEACH,
    LEARN
}
