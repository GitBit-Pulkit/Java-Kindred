package com.kindred.model;

/**
 * One entry of a user's Skill DNA. Maps to one row of the `skills` table.
 *
 * proficiency: 1 (beginner) .. 5 (expert). For a LEARN skill it means
 * "my current level".
 *
 * ownerUsername is not a column of `skills`. It is filled by a JOIN with
 * `users` when the SmartMatch engine loads everybody's skills.
 *
 * The CSV format (skill_name,skill_type,proficiency) is the SAME for export and
 * import, so an exported skill file can be imported again.
 */
// SYLLABUS: CSV import/export - one format used by both CsvExporter and CsvImporter
public class Skill implements CsvExportable {

    public static final String CSV_HEADER = "skill_name,skill_type,proficiency";

    private int skillId;
    private int userId;
    private String ownerUsername;
    private String name;
    private SkillType type;
    private int proficiency;

    /** New skill that is not saved yet (used by the menu and by the CSV importer). */
    public Skill(int userId, String name, SkillType type, int proficiency) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.proficiency = proficiency;
    }

    /** Skill loaded from the database. */
    public Skill(int skillId, int userId, String ownerUsername, String name, SkillType type, int proficiency) {
        this(userId, name, type, proficiency);
        this.skillId = skillId;
        this.ownerUsername = ownerUsername;
    }

    public int getSkillId()          { return skillId; }
    public int getUserId()           { return userId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getName()          { return name; }
    public SkillType getType()       { return type; }
    public int getProficiency()      { return proficiency; }

    public void setSkillId(int skillId)         { this.skillId = skillId; }
    public void setName(String name)            { this.name = name; }
    public void setProficiency(int proficiency) { this.proficiency = proficiency; }

    /** Lower-case, trimmed name. SmartMatch compares skills using this. */
    public String normalizedName() {
        return name.trim().toLowerCase();
    }

    /** e.g. "***--" for proficiency 3. */
    public String stars() {
        return "*".repeat(proficiency) + "-".repeat(5 - proficiency);
    }

    @Override
    public String[] csvValues() {
        return new String[] { name, type.name(), String.valueOf(proficiency) };
    }

    @Override
    public String toString() {
        return String.format("[%d] %-6s %-25s %s", skillId, type, name, stars());
    }
}
