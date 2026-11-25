package com.kindred.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads config/kindred.conf with java.io.StreamTokenizer.
 *
 * StreamTokenizer breaks a character stream into TOKENS:
 *   TT_WORD   -> a word such as  db.url   (st.sval)
 *   TT_NUMBER -> a number such as 5      (st.nval, a double)
 *   '"'       -> a quoted string         (st.sval)
 *   TT_EOL    -> end of a line (only because eolIsSignificant(true))
 *   TT_EOF    -> end of the file
 *
 * Every line must be:   key = value
 * A malformed line is reported with its line number and skipped. A missing
 * file means defaults are used. The application never crashes because of
 * a bad config file.
 */
public final class ConfigLoader {

    // SYLLABUS: Generics - diamond operator infers <String, String>
    private static final Map<String, String> values = new HashMap<>();

    private ConfigLoader() { }   // utility class, never instantiated

    /** Parses the file at 'path'. Safe to call even if the file is missing or broken. */
    public static void load(String path) {
        values.clear();
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("[Config] " + path + " not found - using default settings.");
            return;
        }

        // SYLLABUS: FileReader (character stream) + try-with-resources (auto close)
        try (FileReader reader = new FileReader(file)) {
            // SYLLABUS: StreamTokenizer
            StreamTokenizer st = new StreamTokenizer(reader);
            st.commentChar('#');        // '#' starts a comment until end of line
            st.ordinaryChar('/');       // by default '/' is a comment char; turn that off
            st.wordChars('_', '_');     // allow keys like max_hours
            st.quoteChar('"');          // "..." is one string token
            st.eolIsSignificant(true);  // we want TT_EOL tokens to know where a line ends

            // A tiny state machine: 0=expect key, 1=expect '=', 2=expect value,
            // 3=expect end of line, 4=skipping the rest of a malformed line
            int state = 0;
            String key = null;
            int token;

            while ((token = st.nextToken()) != StreamTokenizer.TT_EOF) {
                if (token == StreamTokenizer.TT_EOL) {
                    if (state == 1 || state == 2) {
                        warn(st.lineno() - 1, "incomplete line (expected: key = value)");
                    }
                    state = 0;
                    continue;
                }
                switch (state) {
                    case 0:
                        if (token == StreamTokenizer.TT_WORD) {
                            key = st.sval;
                            state = 1;
                        } else {
                            warn(st.lineno(), "a line must start with a key name");
                            state = 4;
                        }
                        break;
                    case 1:
                        if (token == '=') {
                            state = 2;
                        } else {
                            warn(st.lineno(), "missing '=' after '" + key + "'");
                            state = 4;
                        }
                        break;
                    case 2:
                        String value = null;
                        if (token == StreamTokenizer.TT_WORD || token == '"') {
                            value = st.sval;
                        } else if (token == StreamTokenizer.TT_NUMBER) {
                            value = numberToText(st.nval);
                        }
                        if (value != null) {
                            values.put(key, value);
                            state = 3;
                        } else {
                            warn(st.lineno(), "invalid value for '" + key + "' (use quotes for text)");
                            state = 4;
                        }
                        break;
                    case 3:
                        warn(st.lineno(), "unexpected extra text after the value of '" + key + "'");
                        state = 4;
                        break;
                    default:
                        // state 4: ignore tokens until the end of this line
                        break;
                }
            }
            System.out.println("[Config] Loaded " + values.size() + " settings from " + path);
        } catch (IOException e) {
            System.out.println("[Config] Could not read " + path + " (" + e.getMessage() + ") - using defaults.");
        }
    }

    private static void warn(int line, String problem) {
        System.out.println("[Config] Line " + line + " skipped: " + problem);
    }

    /** 5.0 -> "5", but 2.5 stays "2.5". */
    private static String numberToText(double number) {
        if (number == Math.floor(number) && !Double.isInfinite(number)) {
            return String.valueOf((long) number);
        }
        return String.valueOf(number);
    }

    public static String getString(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    /** Returns the int value, or the default if missing or not a number. */
    public static int getInt(String key, int defaultValue) {
        String text = values.get(key);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            System.out.println("[Config] '" + key + "' is not a whole number - using " + defaultValue);
            return defaultValue;
        }
    }
}
