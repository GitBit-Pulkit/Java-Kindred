package com.kindred.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Makes a byte-for-byte BACKUP copy of an exported file using
 * FileInputStream / FileOutputStream (BYTE streams).
 *
 * Byte streams vs character streams:
 *   - FileReader/FileWriter (character streams) read and write TEXT and handle
 *     character encoding. They are used for CSV files.
 *   - FileInputStream/FileOutputStream (byte streams) copy RAW BYTES and do not
 *     care what the file contains. That is right for an exact backup copy.
 */
public final class FileBackupUtil {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private FileBackupUtil() { }

    /**
     * Copies 'sourcePath' into 'backupDir' as <name>_<timestamp>.bak.
     * @return the backup file that was created
     */
    // SYLLABUS: FileInputStream + FileOutputStream (byte streams) + try-with-resources
    public static File backup(String sourcePath, String backupDir) throws IOException {
        File source = new File(sourcePath);
        if (!source.isFile()) {
            throw new FileNotFoundException("File not found: " + sourcePath);
        }
        File dir = new File(backupDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create folder " + backupDir);
        }
        File target = new File(dir, source.getName() + "_" + LocalDateTime.now().format(STAMP) + ".bak");

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024];    // read 1 KB at a time
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {   // -1 = end of file
                out.write(buffer, 0, bytesRead);
            }
        }
        return target;
    }
}
