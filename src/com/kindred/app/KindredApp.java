package com.kindred.app;

import com.kindred.db.DBConnection;
import com.kindred.db.DatabaseInitializer;
import com.kindred.model.CreditTransaction;
import com.kindred.model.Match;
import com.kindred.model.Notification;
import com.kindred.model.Skill;
import com.kindred.model.SkillSession;
import com.kindred.model.SkillType;
import com.kindred.model.User;
import com.kindred.service.AdminService;
import com.kindred.service.AuthService;
import com.kindred.service.CreditService;
import com.kindred.service.DataService;
import com.kindred.service.MatchService;
import com.kindred.service.NotificationService;
import com.kindred.service.ServiceException;
import com.kindred.service.SessionService;
import com.kindred.service.SkillService;
import com.kindred.util.CollectionUtils;
import com.kindred.util.ConfigLoader;
import com.kindred.util.InputClosedException;
import com.kindred.util.InputHelper;

import java.util.List;
import java.util.Optional;

/**
 * ENTRY POINT and console user interface of Kindred.
 *
 * This class only shows menus, reads input (InputHelper) and calls services.
 * It contains NO SQL and NO business rules (layered architecture):
 *
 *   app (menus)  ->  service (rules)  ->  dao (SQL)  ->  db (Connection)  ->  MySQL
 *
 * Every service call is wrapped in try/catch(ServiceException), so any problem
 * is shown as a message and the menu continues. The app never crashes on bad input.
 */
public class KindredApp {

    private static final String CONFIG_FILE = "config/kindred.conf";
    private static final int PAGE_SIZE = 5;

    // ---- services (created once, shared by all menus)
    private final AuthService authService = new AuthService();
    private final SkillService skillService = new SkillService();
    private final MatchService matchService = new MatchService();   // uses CompatibilityScorer.BALANCED lambda
    private final CreditService creditService = new CreditService();
    private final AdminService adminService = new AdminService();
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final DataService dataService;

    private User currentUser;   // null when nobody is logged in

    public KindredApp() {
        notificationService = new NotificationService(ConfigLoader.getInt("notification.queue.capacity", 20));
        sessionService = new SessionService(skillService, creditService, notificationService);
        dataService = new DataService(skillService, sessionService, creditService, adminService);
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KINDRED  -  peer-to-peer skill exchange");
        System.out.println("   Teach what you know. Learn what you love.");
        System.out.println("==================================================");

        ConfigLoader.load(CONFIG_FILE);      // StreamTokenizer config file
        DBConnection.init();

        if (!DBConnection.testConnection()) {
            System.out.println("\nKindred needs MySQL. Check that the MySQL server is running and that");
            System.out.println("db.url / db.user / db.password in " + CONFIG_FILE + " are correct.");
            return;
        }
        if (!DatabaseInitializer.createTables()) {
            return;
        }
        System.out.println("[DB] Connected to MySQL. Tables are ready.");

        new KindredApp().run();
    }

    private void run() {
        notificationService.start();   // background consumer thread starts here
        try {
            welcomeMenu();
        } catch (InputClosedException e) {
            System.out.println("\n\nInput closed - exiting Kindred.");
        } finally {
            notificationService.shutdown();   // drain the queue and stop the thread
            System.out.println("Goodbye from Kindred!");
        }
    }

    // ================================================================= WELCOME

    private void welcomeMenu() {
        while (true) {
            System.out.println("\n========== WELCOME ==========");
            System.out.println(" 1. Register");
            System.out.println(" 2. Login");
            System.out.println(" 0. Exit");
            int choice = InputHelper.readInt("Choose: ");
            switch (choice) {
                case 1 -> register();
                case 2 -> login();
                case 0 -> { return; }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void register() {
        System.out.println("\n--- Create your Kindred account ---");
        String username = InputHelper.readNonEmpty("Username (3-20 letters/digits/_): ");
        String fullName = InputHelper.readNonEmpty("Full name: ");
        String password = InputHelper.readPassword("Password (min 6 chars): ");
        String confirm = InputHelper.readPassword("Confirm password: ");
        if (!password.equals(confirm)) {
            System.out.println("Passwords do not match.");
            return;
        }
        try {
            User user = authService.register(username, fullName, password);
            System.out.println("Account created! You received " + user.getCredits() + " welcome credits.");
            if (user.isAdmin()) {
                System.out.println("You are the first user, so you are the ADMIN of this Kindred instance.");
            }
        } catch (ServiceException e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    private void login() {
        String username = InputHelper.readNonEmpty("Username: ");
        String password = InputHelper.readPassword("Password: ");
        try {
            currentUser = authService.login(username, password);
            notificationService.setActiveUser(currentUser.getUserId());
            System.out.println("\nWelcome back, " + currentUser.getFullName() + "!");
            int unread = notificationService.countUnread(currentUser.getUserId());
            if (unread > 0) {
                System.out.println("You have " + unread + " unread notification(s).");
            }
            mainMenu();
        } catch (ServiceException e) {
            System.out.println("Login failed: " + e.getMessage());
        } finally {
            currentUser = null;
            notificationService.setActiveUser(-1);
        }
    }

    // ================================================================= MAIN MENU

    private void mainMenu() {
        while (true) {
            if (!reloadCurrentUser()) {
                return;
            }
            int unread = notificationService.countUnread(currentUser.getUserId());
            System.out.println("\n================ KINDRED ================");
            System.out.println(" " + currentUser.getUsername() + " (" + currentUser.getRole() + ")"
                    + "  |  Wallet: " + currentUser.getCredits() + " credits"
                    + "  |  Unread: " + unread);
            System.out.println("-----------------------------------------");
            System.out.println(" 1. My profile & wallet");
            System.out.println(" 2. Skill DNA (teach / learn)");
            System.out.println(" 3. SmartMatch - find skill partners");
            System.out.println(" 4. Sessions");
            System.out.println(" 5. Notifications");
            System.out.println(" 6. Import / Export data");
            if (currentUser.isAdmin()) {
                System.out.println(" 7. Admin panel");
            }
            System.out.println(" 0. Logout");
            int choice = InputHelper.readInt("Choose: ");
            switch (choice) {
                case 1 -> profileMenu();
                case 2 -> skillMenu();
                case 3 -> smartMatchMenu();
                case 4 -> sessionMenu();
                case 5 -> notificationMenu();
                case 6 -> dataMenu();
                case 7 -> {
                    if (currentUser.isAdmin()) adminMenu();
                    else System.out.println("Invalid option.");
                }
                case 0 -> {
                    System.out.println("Logged out.");
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /** Re-reads the user from MySQL so the balance shown is always current. */
    private boolean reloadCurrentUser() {
        try {
            Optional<User> fresh = authService.refresh(currentUser);
            if (fresh.isEmpty()) {
                System.out.println("Your account no longer exists. Logging out.");
                return false;
            }
            currentUser = fresh.get();
            return true;
        } catch (ServiceException e) {
            System.out.println(e.getMessage());
            return true;   // keep the old copy; the next action may still work
        }
    }

    // ================================================================= PROFILE

    private void profileMenu() {
        while (true) {
            reloadCurrentUser();
            System.out.println("\n--------- MY PROFILE ---------");
            System.out.println(" Username : " + currentUser.getUsername());
            System.out.println(" Name     : " + currentUser.getFullName());
            System.out.println(" Role     : " + currentUser.getRole());
            System.out.println(" Wallet   : " + currentUser.getCredits() + " SkillCredits");
            System.out.println(" Joined   : " + currentUser.getCreatedAt());
            System.out.println("------------------------------");
            System.out.println(" 1. Credit transaction history");
            System.out.println(" 2. Change full name");
            System.out.println(" 3. Change password");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> {
                        List<CreditTransaction> history = creditService.getHistory(currentUser.getUserId());
                        System.out.println("Total earned: " + creditService.totalEarned(history, currentUser.getUserId())
                                + "  |  Total spent: " + creditService.totalSpent(history, currentUser.getUserId()));
                        showPaged("CREDIT HISTORY", history);
                    }
                    case 2 -> {
                        authService.changeFullName(currentUser, InputHelper.readNonEmpty("New full name: "));
                        System.out.println("Name updated.");
                    }
                    case 3 -> {
                        String oldPw = InputHelper.readPassword("Current password: ");
                        String newPw = InputHelper.readPassword("New password: ");
                        String confirm = InputHelper.readPassword("Confirm new password: ");
                        if (!newPw.equals(confirm)) {
                            System.out.println("Passwords do not match.");
                        } else {
                            authService.changePassword(currentUser, oldPw, newPw);
                            System.out.println("Password changed.");
                        }
                    }
                    case 0 -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // ================================================================= SKILL DNA

    private void skillMenu() {
        while (true) {
            System.out.println("\n--------- SKILL DNA ---------");
            System.out.println(" 1. View my skills");
            System.out.println(" 2. Add a skill I can TEACH");
            System.out.println(" 3. Add a skill I want to LEARN");
            System.out.println(" 4. Update a skill");
            System.out.println(" 5. Delete a skill");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> printSkills();
                    case 2 -> addSkill(SkillType.TEACH);
                    case 3 -> addSkill(SkillType.LEARN);
                    case 4 -> {
                        if (printSkills()) {
                            int id = InputHelper.readInt("Skill id to update: ");
                            String name = InputHelper.readLine("New name (Enter to keep current): ");
                            int level = InputHelper.readIntInRange("New proficiency (1-5): ", 1, 5);
                            skillService.updateSkill(currentUser.getUserId(), id, name, level);
                            System.out.println("Skill updated.");
                        }
                    }
                    case 5 -> {
                        if (printSkills()) {
                            int id = InputHelper.readInt("Skill id to delete: ");
                            skillService.deleteSkill(currentUser.getUserId(), id);
                            System.out.println("Skill deleted.");
                        }
                    }
                    case 0 -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /** Prints the Skill DNA. Returns false if it is empty. */
    private boolean printSkills() throws ServiceException {
        List<Skill> skills = skillService.getSkills(currentUser.getUserId());
        if (skills.isEmpty()) {
            System.out.println("Your Skill DNA is empty. Add what you can teach and what you want to learn!");
            return false;
        }
        System.out.println("\n  id   type   skill                     level");
        skills.forEach(s -> System.out.println("  " + s));   // SYLLABUS: Lambda with forEach
        return true;
    }

    private void addSkill(SkillType type) throws ServiceException {
        String name = InputHelper.readNonEmpty("Skill name (e.g. Java, Guitar, Spanish): ");
        String question = type == SkillType.TEACH
                ? "How good are you at it? (1-5): "
                : "Your current level (1-5): ";
        int level = InputHelper.readIntInRange(question, 1, 5);
        Skill skill = skillService.addSkill(currentUser.getUserId(), name, type, level);
        System.out.println("Added " + skill.getType() + " skill: " + skill.getName());
    }

    // ================================================================= SMARTMATCH

    private void smartMatchMenu() {
        try {
            int limit = ConfigLoader.getInt("match.limit", 10);
            List<Match> matches = matchService.findMatches(currentUser, limit);

            if (!matches.isEmpty() && InputHelper.readYesNo("Show only MUTUAL swaps (both can teach each other)?")) {
                matches = matchService.filter(matches, Match::isMutual);  // SYLLABUS: Method reference as a Predicate
            }
            if (matches.isEmpty()) {
                System.out.println("No compatible users found yet. Add more skills or invite friends!");
                return;
            }

            System.out.println("\n======= YOUR TOP MATCHES =======");
            for (int i = 0; i < matches.size(); i++) {
                System.out.println((i + 1) + ". " + matches.get(i));
            }

            int pick = InputHelper.readIntInRange("\nRequest a session with match number (0 = back): ", 0, matches.size());
            if (pick == 0) {
                return;
            }
            Match chosen = matches.get(pick - 1);
            if (chosen.getTheyCanTeachMe().isEmpty()) {
                System.out.println(chosen.getUsername() + " does not teach anything on your LEARN list yet.");
                return;
            }
            List<String> skills = chosen.getTheyCanTeachMe();
            for (int i = 0; i < skills.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + skills.get(i));
            }
            int skillPick = InputHelper.readIntInRange("Which skill do you want to learn? ", 1, skills.size());
            int hours = askHours();
            SkillSession s = sessionService.requestSession(currentUser, chosen.getUsername(), skills.get(skillPick - 1), hours);
            System.out.println("Request sent! Session #" + s.getSessionId() + " is PENDING until "
                    + chosen.getUsername() + " accepts.");
        } catch (ServiceException e) {
            System.out.println("SmartMatch: " + e.getMessage());
        }
    }

    private int askHours() {
        int max = ConfigLoader.getInt("max.session.hours", 4);
        int hours = InputHelper.readIntInRange("How many hours (1-" + max + ")? ", 1, max);
        System.out.println("Cost: " + hours * CreditService.creditsPerHour() + " credits (paid when you mark it completed).");
        return hours;
    }

    // ================================================================= SESSIONS

    private void sessionMenu() {
        while (true) {
            System.out.println("\n----------- SESSIONS -----------");
            System.out.println(" 1. Request a session by username");
            System.out.println(" 2. Incoming requests (accept / reject)");
            System.out.println(" 3. My pending requests (cancel)");
            System.out.println(" 4. Mark a session as completed (pays the teacher)");
            System.out.println(" 5. Session history");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> requestByUsername();
                    case 2 -> handleIncoming();
                    case 3 -> cancelPending();
                    case 4 -> completeSession();
                    case 5 -> showPaged("SESSION HISTORY", sessionService.getHistory(currentUser.getUserId()));
                    case 0 -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void requestByUsername() throws ServiceException {
        String teacher = InputHelper.readNonEmpty("Teacher's username: ");
        String skill = InputHelper.readNonEmpty("Skill to learn: ");
        int hours = askHours();
        SkillSession s = sessionService.requestSession(currentUser, teacher, skill, hours);
        System.out.println("Request sent! Session #" + s.getSessionId() + " is PENDING.");
    }

    private void handleIncoming() throws ServiceException {
        List<SkillSession> incoming = sessionService.getIncomingRequests(currentUser.getUserId());
        if (incoming.isEmpty()) {
            System.out.println("No requests waiting for you.");
            return;
        }
        incoming.forEach(System.out::println);   // SYLLABUS: Method reference System.out::println
        int id = InputHelper.readInt("Session id to answer (0 = back): ");
        if (id == 0) {
            return;
        }
        boolean accept = InputHelper.readYesNo("Accept this session?");
        sessionService.respondToRequest(currentUser, id, accept);
        System.out.println("Session #" + id + (accept ? " ACCEPTED." : " REJECTED."));
    }

    private void cancelPending() throws ServiceException {
        List<SkillSession> pending = sessionService.getMyPendingRequests(currentUser.getUserId());
        if (pending.isEmpty()) {
            System.out.println("You have no pending requests.");
            return;
        }
        pending.forEach(System.out::println);
        int id = InputHelper.readInt("Session id to cancel (0 = back): ");
        if (id != 0) {
            sessionService.cancelRequest(currentUser, id);
            System.out.println("Session #" + id + " cancelled.");
        }
    }

    private void completeSession() throws ServiceException {
        List<SkillSession> active = sessionService.getSessionsToComplete(currentUser.getUserId());
        if (active.isEmpty()) {
            System.out.println("You have no ACCEPTED sessions to complete (only the learner can complete a session).");
            return;
        }
        active.forEach(System.out::println);
        int id = InputHelper.readInt("Session id you finished (0 = back): ");
        if (id == 0) {
            return;
        }
        if (!InputHelper.readYesNo("Confirm the lesson happened and pay the teacher?")) {
            return;
        }
        SkillSession done = sessionService.completeSession(currentUser, id);
        System.out.println("Session #" + id + " COMPLETED. "
                + done.getHours() * CreditService.creditsPerHour() + " credits transferred to "
                + done.getTeacherUsername() + ".");
    }

    // ================================================================= NOTIFICATIONS

    private void notificationMenu() {
        try {
            List<Notification> inbox = notificationService.getInbox(currentUser.getUserId());
            showPaged("NOTIFICATIONS  ((*) = unread)", inbox);
            if (!inbox.isEmpty()) {
                notificationService.markAllRead(currentUser.getUserId());
                System.out.println("All notifications marked as read.");
            }
        } catch (ServiceException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ================================================================= DATA

    private void dataMenu() {
        while (true) {
            System.out.println("\n-------- IMPORT / EXPORT --------");
            System.out.println(" 1. Export my skills to CSV");
            System.out.println(" 2. Export my session history to CSV");
            System.out.println(" 3. Export my credit transactions to CSV");
            System.out.println(" 4. Import skills from a CSV file");
            System.out.println(" 5. Backup an exported file (byte copy)");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> System.out.println("Saved: " + dataService.exportSkills(currentUser));
                    case 2 -> System.out.println("Saved: " + dataService.exportSessions(currentUser));
                    case 3 -> System.out.println("Saved: " + dataService.exportTransactions(currentUser));
                    case 4 -> {
                        System.out.println("CSV format: skill_name,skill_type,proficiency   e.g.  Java,TEACH,4");
                        String path = InputHelper.readNonEmpty("Path of CSV file: ");
                        System.out.println(dataService.importSkills(currentUser, path));
                    }
                    case 5 -> {
                        String path = InputHelper.readNonEmpty("Path of file to back up (e.g. exports/"
                                + currentUser.getUsername() + "_skills.csv): ");
                        System.out.println("Backup created: " + dataService.backupFile(path));
                    }
                    case 0 -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // ================================================================= ADMIN

    private void adminMenu() {
        while (true) {
            System.out.println("\n----------- ADMIN PANEL -----------");
            System.out.println(" 1. List all users");
            System.out.println(" 2. Credit leaderboard (top 5)");
            System.out.println(" 3. All credit transactions");
            System.out.println(" 4. All sessions");
            System.out.println(" 5. Delete a user");
            System.out.println(" 6. Export all users to CSV");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> adminService.getAllUsers(currentUser).forEach(System.out::println);
                    case 2 -> {
                        List<User> top = adminService.getLeaderboard(currentUser, 5);
                        for (int i = 0; i < top.size(); i++) {
                            System.out.println((i + 1) + ". " + top.get(i));
                        }
                    }
                    case 3 -> showPaged("ALL TRANSACTIONS", adminService.getAllTransactions(currentUser));
                    case 4 -> showPaged("ALL SESSIONS", adminService.getAllSessions(currentUser));
                    case 5 -> {
                        adminService.getAllUsers(currentUser).forEach(System.out::println);
                        int id = InputHelper.readInt("User id to delete (0 = back): ");
                        if (id != 0 && InputHelper.readYesNo("Delete user #" + id
                                + " with all their skills, sessions and notifications? This cannot be undone.")) {
                            adminService.deleteUser(currentUser, id);
                            System.out.println("User #" + id + " deleted.");
                        }
                    }
                    case 6 -> System.out.println("Saved: " + dataService.exportAllUsers(currentUser));
                    case 0 -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // ================================================================= helpers

    /**
     * Prints ANY list page by page (5 items per page).
     * Generic method: works for sessions, transactions, notifications...
     */
    // SYLLABUS: Generics - generic method <T>; uses CollectionUtils.page (Stream skip + limit)
    private <T> void showPaged(String title, List<T> items) {
        System.out.println("\n===== " + title + " =====");
        if (items.isEmpty()) {
            System.out.println("(nothing here yet)");
            return;
        }
        int totalPages = (items.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        for (int page = 1; page <= totalPages; page++) {
            System.out.println("-- page " + page + " of " + totalPages + " --");
            CollectionUtils.page(items, page, PAGE_SIZE).forEach(System.out::println);
            if (page < totalPages
                    && InputHelper.readLine("Enter = next page, q = stop: ").equalsIgnoreCase("q")) {
                break;
            }
        }
    }
}
