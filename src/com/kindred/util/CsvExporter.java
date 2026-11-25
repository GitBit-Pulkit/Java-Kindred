package com.kindred.util;

import com.kindred.model.CsvExportable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes any list of CsvExportable objects to a CSV file using FileWriter
 * (a CHARACTER stream).
 *
 * The SAME method exports Users, Skills, Sessions and Transactions, because it
 * accepts List<? extends CsvExportable>.
 */
public final class CsvExporter {

    private CsvExporter() { }

    /**
     * @return number of data rows written (the header is not counted)
     */
    // SYLLABUS: Generics - upper bounded wildcard List<? extends CsvExportable>
    // SYLLABUS: FileWriter (character stream) + try-with-resources
    public static int export(String filePath, String header, List<? extends CsvExportable> rows) throws IOException {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent.getPath());
        }

        // try-with-resources closes (and flushes) the writer even if an exception happens
        try (FileWriter fileWriter = new FileWriter(file);
             BufferedWriter writer = new BufferedWriter(fileWriter)) {
            writer.write(header);
            writer.newLine();
            for (CsvExportable row : rows) {
                writer.write(toCsvLine(row.csvValues()));
                writer.newLine();
            }
        }
        return rows.size();
    }

    /** Joins values with commas, escaping each one. */
    // SYLLABUS: Stream map + collect(joining) with a method reference
    private static String toCsvLine(String[] values) {
        return Arrays.stream(values)
                .map(CsvExporter::escape)
                .collect(Collectors.joining(","));
    }

    /**
     * CSV escaping: a value containing a comma, quote or line break is wrapped in
     * quotes, and inner quotes are doubled  ->  He said "hi"  becomes  "He said ""hi"""
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
