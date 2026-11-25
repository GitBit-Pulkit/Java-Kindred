package com.kindred.util;

import java.io.Console;
import java.util.Scanner;

/**
 * All console input goes through this class (java.util.Scanner).
 * Every method validates input and asks again on bad values, so wrong input
 * never crashes the application.
 */
public final class InputHelper {

    private static final Scanner SCANNER = new Scanner(System.in);

    private InputHelper() { }

    /** Reads one line (trimmed). Throws InputClosedException if the input stream ended. */
    public static String readLine(String prompt) {
        System.out.print(prompt);
        if (!SCANNER.hasNextLine()) {
            throw new InputClosedException();
        }
        return SCANNER.nextLine().trim();
    }

    /** Keeps asking until the user types something. */
    public static String readNonEmpty(String prompt) {
        while (true) {
            String text = readLine(prompt);
            if (!text.isEmpty()) {
                return text;
            }
            System.out.println("  This field cannot be empty.");
        }
    }

    /** Keeps asking until a whole number is typed. */
    // SYLLABUS: Exception handling - NumberFormatException caught so bad input never crashes the app
    public static int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {   // bad input is handled, never a crash
                System.out.println("  Please enter a whole number.");
            }
        }
    }

    /** Keeps asking until a number between min and max (inclusive) is typed. */
    public static int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    public static boolean readYesNo(String prompt) {
        while (true) {
            String text = readLine(prompt + " (y/n): ").toLowerCase();
            if (text.equals("y") || text.equals("yes")) return true;
            if (text.equals("n") || text.equals("no"))  return false;
            System.out.println("  Please type y or n.");
        }
    }

    /**
     * Reads a password. In a real terminal System.console() hides the typed
     * characters. In an IDE console (where System.console() is null) it falls
     * back to a normal visible Scanner line.
     */
    public static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword(prompt);
            if (chars == null) {
                throw new InputClosedException();
            }
            return new String(chars);
        }
        return readLine(prompt);
    }

    public static void pause() {
        readLine("\nPress Enter to continue...");
    }
}
