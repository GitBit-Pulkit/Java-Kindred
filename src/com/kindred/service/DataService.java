package com.kindred.service;

import com.kindred.model.CreditTransaction;
import com.kindred.model.CsvExportable;
import com.kindred.model.Skill;
import com.kindred.model.SkillSession;
import com.kindred.model.User;
import com.kindred.util.ConfigLoader;
import com.kindred.util.CsvExporter;
import com.kindred.util.CsvImporter;
import com.kindred.util.FileBackupUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * DATA IMPORT / EXPORT. Ties the database (through the services) to files:
 *   - export skills, sessions, transactions, all users -> CSV  (FileWriter)
 *   - import skills from CSV                           -> MySQL (FileReader)
 *   - byte-for-byte backup of any exported file        (FileInputStream / FileOutputStream)
 * Every file problem becomes a friendly ServiceException. No crash.
 */
public class DataService {

    private final SkillService skillService;
    private final SessionService sessionService;
    private final CreditService creditService;
    private final AdminService adminService;

    public DataService(SkillService skillService, SessionService sessionService,
                       CreditService creditService, AdminService adminService) {
        this.skillService = skillService;
        this.sessionService = sessionService;
        this.creditService = creditService;
        this.adminService = adminService;
    }

    private String exportDir() {
        return ConfigLoader.getString("export.dir", "exports");
    }

    public String exportSkills(User user) throws ServiceException {
        List<Skill> skills = skillService.getSkills(user.getUserId());
        return write(user.getUsername() + "_skills.csv", Skill.CSV_HEADER, skills);
    }

    public String exportSessions(User user) throws ServiceException {
        List<SkillSession> sessions = sessionService.getHistory(user.getUserId());
        return write(user.getUsername() + "_sessions.csv", SkillSession.CSV_HEADER, sessions);
    }

    public String exportTransactions(User user) throws ServiceException {
        List<CreditTransaction> txns = creditService.getHistory(user.getUserId());
        return write(user.getUsername() + "_transactions.csv", CreditTransaction.CSV_HEADER, txns);
    }

    public String exportAllUsers(User admin) throws ServiceException {
        List<User> users = adminService.getAllUsers(admin);
        return write("all_users.csv", User.CSV_HEADER, users);
    }

    /** The SAME helper writes four different model types thanks to List<? extends CsvExportable>. */
    // SYLLABUS: Generics - upper bounded wildcard List<? extends CsvExportable>
    private String write(String fileName, String header, List<? extends CsvExportable> rows)
            throws ServiceException {
        String path = exportDir() + File.separator + fileName;
        try {
            int count = CsvExporter.export(path, header, rows);
            return path + " (" + count + " rows)";
        } catch (IOException e) {
            throw new ServiceException("Export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reads skills from a CSV file and saves the valid ones to MySQL.
     * @return a short summary for the user
     */
    public String importSkills(User user, String filePath) throws ServiceException {
        List<Skill> parsed = new ArrayList<>();   // passed where List<? super Skill> is expected
        int malformed;
        try {
            malformed = CsvImporter.readSkills(filePath, user.getUserId(), parsed);
        } catch (FileNotFoundException e) {
            throw new ServiceException("File not found: " + filePath);
        } catch (IOException e) {
            throw new ServiceException("Could not read file: " + e.getMessage(), e);
        }
        int added = skillService.addImportedSkills(user.getUserId(), parsed);
        return "Imported " + added + " skill(s). Malformed lines skipped: " + malformed
                + ". Duplicates/invalid skipped: " + (parsed.size() - added) + ".";
    }

    public String backupFile(String filePath) throws ServiceException {
        try {
            File backup = FileBackupUtil.backup(filePath, exportDir() + File.separator + "backup");
            return backup.getPath() + " (" + backup.length() + " bytes)";
        } catch (IOException e) {
            throw new ServiceException("Backup failed: " + e.getMessage(), e);
        }
    }
}
