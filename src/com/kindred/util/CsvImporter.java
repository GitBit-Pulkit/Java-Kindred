package com.kindred.util;

import com.kindred.model.Skill;
import com.kindred.model.SkillType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads skills from a CSV file using FileReader (a CHARACTER stream).
 *
 * Expected format (the same format Kindred exports):
 *     skill_name,skill_type,proficiency
 *     Java,TEACH,4
 *     "Guitar, acoustic",LEARN,1
 *
 * MALFORMED lines (wrong column count, unknown type, proficiency not 1-5, ...)
 * are skipped with a message naming the line number. One bad line never stops
 * the import and never crashes the app.
 */
public final class CsvImporter {

    private CsvImporter() { }

    /**
     * Parses the file and ADDS every valid skill into 'sink'.
     *
     * List<? super Skill> means "a list that can accept Skill objects", so a
     * List<Skill> or a List<Object> can be passed. Writing into a collection
     * is exactly what lower bounded wildcards are for.
     *
     * @return number of malformed lines that were skipped
     */
    // SYLLABUS: Generics - lower bounded wildcard List<? super Skill>
    // SYLLABUS: FileReader (character stream) + try-with-resources
    public static int readSkills(String filePath, int userId, List<? super Skill> sink) throws IOException {
        int skipped = 0;
        int lineNumber = 0;

        try (FileReader fileReader = new FileReader(filePath);
             BufferedReader reader = new BufferedReader(fileReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (lineNumber == 1 && line.trim().equalsIgnoreCase(Skill.CSV_HEADER)) {
                    continue; // header row
                }
                try {
                    sink.add(parseSkill(line, userId));
                } catch (IllegalArgumentException e) {
                    System.out.println("  [Import] Line " + lineNumber + " skipped: " + e.getMessage());
                    skipped++;
                }
            }
        }
        return skipped;
    }

    /** Converts one CSV line into a Skill, or throws IllegalArgumentException explaining what is wrong. */
    private static Skill parseSkill(String line, int userId) {
        List<String> fields = splitCsvLine(line);
        if (fields.size() != 3) {
            throw new IllegalArgumentException("expected 3 columns but found " + fields.size());
        }
        String name = fields.get(0).trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new IllegalArgumentException("skill name must be 1-40 characters");
        }
        SkillType type;
        try {
            type = SkillType.valueOf(fields.get(1).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("skill type must be TEACH or LEARN, not '" + fields.get(1).trim() + "'");
        }
        int proficiency;
        try {
            proficiency = Integer.parseInt(fields.get(2).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("proficiency '" + fields.get(2).trim() + "' is not a number");
        }
        if (proficiency < 1 || proficiency > 5) {
            throw new IllegalArgumentException("proficiency must be between 1 and 5");
        }
        return new Skill(userId, name, type, proficiency);
    }

    /** Splits a CSV line on commas, respecting "quoted, values" and "" escapes. */
    private static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');    // "" inside quotes = one literal quote
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("a quoted value is never closed");
        }
        fields.add(current.toString());
        return fields;
    }
}
