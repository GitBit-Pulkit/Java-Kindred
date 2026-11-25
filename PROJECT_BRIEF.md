# PROJECT BRIEF: Kindred (Peer-to-Peer Skill Exchange, Java 21 + MySQL)

> **How to use this file:** Give this whole document to any LLM / AI agent and say:
> *"This is my Java project Kindred. Prepare me for my viva: explain the concepts,
> ask me likely questions, and quiz me on how each feature works."*
> It explains what the project is, how it is built, where every syllabus concept is
> used, how each feature works step by step, and how the features connect.

---

## 1. What Kindred is

Kindred is a **console-based peer-to-peer skill exchange platform** built on a
**time-bank economy**. Nobody pays money. If you **teach** someone for 1 hour, you
**earn 1 SkillCredit**. You then **spend** credits to **learn** a different skill from
someone else. Every new account gets **5 welcome credits** to start.

It is a real, working application, not a mockup. Every feature reads and writes a
**MySQL database through raw JDBC**. Passwords are **BCrypt-hashed**. Credit transfers
are **atomic JDBC transactions**. Notifications are delivered by a **background thread**
using a producer-consumer queue. Data can be **exported/imported as CSV**, and settings
are read from a **config file with StreamTokenizer**.

### The 7 core features
1. **Authentication:** register and log in with BCrypt-hashed passwords. Role-based access: USER or ADMIN. The first registered user becomes ADMIN.
2. **Skill DNA:** each user lists skills they can **TEACH** and want to **LEARN**, each with a proficiency of 1-5. Users can view, add, update and delete them.
3. **SmartMatch engine:** finds users with mutual skill overlap, calculates a **compatibility score (0-100%)**, and ranks them. Built with the **Stream API and lambdas**.
4. **Session management:** request a session, then the teacher accepts or rejects it, then the learner marks it completed. A learner can cancel while it is pending. Status and history are tracked.
5. **Credit economy:** a wallet per user. On completion, credits move learner → teacher inside **one JDBC transaction** (`setAutoCommit(false)` / `commit()` / `rollback()`).
6. **Background notifications:** a **producer-consumer queue** (`synchronized` blocks, `wait()`, `notifyAll()`, guarded `while` loops). A raw `Thread` saves notifications to the database and shows live alerts without freezing the menu.
7. **Data import/export:** CSV export of skills, sessions, transactions and all users (`FileWriter`). CSV import of skills (`FileReader`) with malformed-line handling. Byte-for-byte backup (`FileInputStream`/`FileOutputStream`). Config file parsed with `StreamTokenizer`.

---

## 2. Tech stack and constraints

| Item | Choice |
|---|---|
| Language | Java 21 (switch arrow syntax `case 1 ->` is used in menus) |
| Database | MySQL 8 (tested on 8.4) |
| DB access | **Raw JDBC only** (`java.sql.*`): DriverManager, Connection, PreparedStatement, ResultSet |
| Libraries | `lib/mysql-connector-j-9.1.0.jar` (JDBC driver), `lib/jbcrypt-0.4.jar` (BCrypt). Nothing else |
| Build | **No Maven/Gradle/Spring/Hibernate/JPA.** `build.bat` runs `javac -cp "lib\*"`; `run.bat` runs `java -cp "out;lib\*" com.kindred.app.KindredApp` |
| UI | Console menus with `java.util.Scanner` |
| Security | BCrypt hashing; **PreparedStatement with `?` everywhere** (no plain `Statement` object), so no SQL injection |

---

## 3. Architecture (layered)

```
  com.kindred.app      KindredApp          <- menus + input only (no SQL, no business rules)
        |
  com.kindred.service  AuthService, SkillService, MatchService, SessionService,
        |              CreditService, AdminService, DataService, NotificationService
        |              (+ NotificationQueue, NotificationConsumer, CompatibilityScorer, ServiceException)
        |              <- business rules, validation, transactions, threads
        |
  com.kindred.dao      GenericDao<T,ID>, UserDao, SkillDao, SessionDao, TransactionDao, NotificationDao
        |              <- ALL SQL lives here (PreparedStatement + ResultSet mapping)
        |
  com.kindred.db       DBConnection (DriverManager), DatabaseInitializer (CREATE TABLE IF NOT EXISTS)
        |
      MySQL

  com.kindred.model    User, Skill, SkillSession, CreditTransaction, Match, Notification,
                       enums Role / SkillType / SessionStatus / TransactionType, interface CsvExportable
  com.kindred.util     ConfigLoader (StreamTokenizer), InputHelper (Scanner), PasswordUtil (BCrypt),
                       CsvExporter, CsvImporter, FileBackupUtil, CollectionUtils (generic helpers),
                       InputClosedException
```

**Why layers?** Each layer has one job. The menu never writes SQL, and the DAO never
decides business rules. You could swap the console for a GUI and only `app` would change.

**Error handling strategy:** DAOs throw `SQLException`. Services catch it (and
`IOException`) and wrap it in **`ServiceException`**, which carries a user-friendly
message. The menu catches `ServiceException` and prints it. Bad numbers are re-asked by
`InputHelper`. End of input throws `InputClosedException`, which leads to a clean
shutdown. **The app never crashes on bad input.**

---

## 4. Folder and file guide (purpose of each file)

```
kindred/
  build.bat, run.bat          compile / run (no Maven)
  config/kindred.conf         settings read by StreamTokenizer (DB URL, user, password, credits, limits)
  lib/                        mysql-connector-j + jbcrypt JARs
  sql/schema.sql              reference copy of the schema (tables are auto-created anyway)
  sample_data/sample_skills.csv  import demo with valid AND deliberately malformed lines
  exports/                    created at runtime: CSV exports + exports/backup/*.bak
  src/com/kindred/...         source code
```

### model (plain data classes)
| File | Purpose |
|---|---|
| `User` | Account: id, username, fullName, **passwordHash**, role, credits, createdAt. Implements `CsvExportable` (no hash in CSV) and `Comparable<User>` (by credits, for the leaderboard). |
| `Skill` | One Skill DNA entry: name, TEACH/LEARN, proficiency 1-5, ownerUsername (from a JOIN). `normalizedName()` (lower-case) is used for matching. CSV format = import format. |
| `SkillSession` | A session: learner, teacher, skill, hours, status, timestamps. |
| `CreditTransaction` | A ledger row: type, from (null = SYSTEM), to, amount, sessionId. `welcomeBonus()` factory. |
| `Match` | SmartMatch result (in memory only): user, theyCanTeachMe, theyWantFromMe, score, `isMutual()`. |
| `Notification` | Inbox message: userId, message, read flag. |
| `Role`, `SkillType`, `SessionStatus`, `TransactionType` | Enums mirroring MySQL ENUM columns. |
| `CsvExportable` | Interface: `String[] csvValues()`. Used as the upper bound in `List<? extends CsvExportable>`. |

### dao (SQL only)
| File | Purpose |
|---|---|
| `GenericDao<T, ID>` | **Generic interface**: `create`, `findById` (returns `Optional<T>`), `findAll`, `update`, `delete`. |
| `UserDao` | CRUD on `users`, plus `findByUsername`, `countUsers`, **`getCreditsForUpdate` (SELECT … FOR UPDATE row lock)** and `adjustCredits`. The transaction-aware methods take a `Connection` parameter. |
| `SkillDao` | CRUD on `skills` (JOINed with users), `findByUser`, `findAllExceptUser` (SmartMatch input), `userHasSkill`. |
| `SessionDao` | CRUD on `sessions` (JOINs users twice for learner and teacher names), `findByIdForUpdate`, **conditional `updateStatus(..., expectedStatus)`**, `existsOpenSession`. |
| `TransactionDao` | Ledger: `insert` (inside a transaction), `findByUser`, `findAll`. **No update/delete, because the ledger is append-only.** Handles NULLs with `setNull` / `wasNull`. |
| `NotificationDao` | `insert` (called by the background thread), `findByUser`, `countUnread`, `markAllRead`. |

### db
| File | Purpose |
|---|---|
| `DBConnection` | Reads db.url/db.user/db.password from config and returns `DriverManager.getConnection(...)`. Each call gives a new Connection, closed by try-with-resources. `testConnection()` runs at start-up. |
| `DatabaseInitializer` | Runs `CREATE TABLE IF NOT EXISTS` for the 5 tables through **PreparedStatement**. The URL has `createDatabaseIfNotExist=true`, so the database itself is created too. |

### service (rules)
| File | Purpose |
|---|---|
| `AuthService` | Validates and registers users (**transaction**: user row + WELCOME_BONUS ledger row). Login with BCrypt check. Change name/password. `refresh()` reloads the balance. |
| `SkillService` | Add/view/update/delete skills with validation and ownership checks. Duplicates are caught via MySQL UNIQUE (`SQLIntegrityConstraintViolationException`). `findTeachSkillName` uses Stream + Optional. |
| `CompatibilityScorer` | **Custom `@FunctionalInterface`** with a `BALANCED` lambda implementation. |
| `MatchService` | **SmartMatch** Stream pipeline: groupingBy, map, filter, sorted, limit, collect. `filter(matches, Predicate<? super Match>)`. |
| `SessionService` | Request/accept/reject/cancel/complete with permission checks. Produces notifications. |
| `CreditService` | **Atomic credit transfer** (`completeSession`), history, and totals via `CollectionUtils.sum`. |
| `AdminService` | Admin-only: all users, leaderboard (`topN`), all transactions/sessions, delete user. Re-checks the role every time. |
| `NotificationQueue` | **Bounded buffer**: `produce()` / `consume()` with `synchronized(lock)`, `wait()`, `notifyAll()` and guarded while loops. Graceful `shutdown()`. |
| `NotificationConsumer` | **Raw `Thread`** (extends Thread). Loop: consume, insert into DB, live alert if the recipient is logged in. `volatile activeUserId`. |
| `NotificationService` | Owns the queue and consumer: `start()`, `notifyUser()` (producer), inbox, `shutdown()` (drain + `join`). |
| `DataService` | Export (4 kinds), import skills, backup file. Turns IO errors into ServiceExceptions. |
| `ServiceException` | Checked exception carrying a safe, friendly message. |

### util
| File | Purpose |
|---|---|
| `ConfigLoader` | **StreamTokenizer** parser for `key = value` lines. Handles comments, quotes and numbers. Malformed lines are reported and skipped; a missing file means defaults. |
| `InputHelper` | Scanner wrapper: `readInt`, `readIntInRange`, `readYesNo`, `readNonEmpty`, `readPassword` (hidden via `System.console()` when available). |
| `PasswordUtil` | `BCrypt.hashpw` with `gensalt(12)` and `BCrypt.checkpw`. |
| `CsvExporter` | Writes `List<? extends CsvExportable>` with **FileWriter** + BufferedWriter. Escapes commas and quotes. |
| `CsvImporter` | Reads skills with **FileReader** + BufferedReader into `List<? super Skill>`. Quote-aware splitter. Malformed lines are skipped with line numbers. |
| `FileBackupUtil` | Byte copy with **FileInputStream / FileOutputStream** and a 1 KB buffer. |
| `CollectionUtils` | Generic helpers: `topN` (bounded `<T extends Comparable<? super T>>`), `page` (skip + limit), `sum(List<? extends Number>)`. |
| `InputClosedException` | Signals that console input ended, which triggers a graceful exit. |

### app
| File | Purpose |
|---|---|
| `KindredApp` | `main()`: load config, connect, create tables, start the notification thread. Welcome menu (register/login), then the main menu: Profile & wallet, Skill DNA, SmartMatch, Sessions, Notifications, Import/Export, Admin panel (ADMIN only). Generic `showPaged<T>()` shows long lists 5 per page. |

---

## 5. Database design (5 tables)

| Table | Key columns | Protection built into MySQL |
|---|---|---|
| `users` | user_id PK, username UNIQUE, full_name, password_hash CHAR(60), role ENUM, credits, created_at | **CHECK (credits >= 0)**, so a wallet can never go negative even if code had a bug |
| `skills` | skill_id PK, user_id FK, skill_name, skill_type ENUM, proficiency | CHECK 1-5; **UNIQUE(user_id, skill_name, skill_type)** stops duplicates (case-insensitive collation); FK ON DELETE CASCADE |
| `sessions` | session_id PK, learner_id FK, teacher_id FK, skill_name, hours, status ENUM, requested_at, updated_at (auto-updates) | CHECK hours > 0; FKs CASCADE |
| `transactions` | txn_id PK, session_id **UNIQUE** (nullable), from_user_id (NULL = system), to_user_id, amount, txn_type ENUM | CHECK amount > 0; **a session can be paid only once**; FKs SET NULL keep history when a user is deleted |
| `notifications` | notification_id PK, user_id FK, message, is_read, created_at | FK CASCADE |

Relationships: one user has many skills, many sessions (as learner or teacher), many
transactions and many notifications. One session has at most one payment transaction.

---

## 6. How each feature works (step by step)

### 6.1 Start-up
`KindredApp.main()` runs these steps in order:
1. `ConfigLoader.load("config/kindred.conf")` parses the settings with StreamTokenizer.
2. `DBConnection.init()` reads the DB settings.
3. `testConnection()` checks that MySQL is reachable. If not, it prints a helpful message and exits.
4. `DatabaseInitializer.createTables()` creates any missing tables.
5. `new KindredApp()` wires the services. `NotificationService.start()` starts the background consumer thread.
6. The welcome menu appears.

On exit (option 0, or the input stream ending), `notificationService.shutdown()` drains the queue and `join()`s the thread.

### 6.2 Registration (Authentication + JDBC transaction)
1. The menu reads the username, full name, password and confirmation. Passwords are hidden in a real terminal.
2. `AuthService.register` validates the input:
   - username matches the regex `[A-Za-z0-9_]{3,20}`
   - name is 1-60 characters
   - password is 6-64 characters
3. `PasswordUtil.hash()` produces the BCrypt hash, with a random salt and cost factor 12.
4. `conn.setAutoCommit(false)` starts a transaction. Inside it:
   - check the username is free
   - `countUsers == 0`? Then the role is **ADMIN**, else USER
   - INSERT the user with 5 starting credits and get the generated id
   - INSERT a `WELCOME_BONUS` ledger row
   - `commit()`
   On any error, `rollback()`. A UNIQUE violation becomes "Username already taken".
5. Result: the user and the ledger are always consistent, because every credit has a ledger entry.

### 6.3 Login
`AuthService.login` runs `findByUsername` and then `BCrypt.checkpw(typed, storedHash)`.
The same error message ("Invalid username or password") is shown for an unknown user
and for a wrong password, so attackers cannot discover which usernames exist. On success,
the current user is stored, `NotificationService.setActiveUser(id)` enables live alerts,
and the unread count is shown. The main menu **reloads the user from MySQL on every loop**,
so the wallet is always current.

### 6.4 Skill DNA (CRUD)
- **Create:** `SkillService.addSkill` cleans the name (trim, collapse spaces, allowed characters, max 40) and validates proficiency (1-5). Then `SkillDao.create` (INSERT). A duplicate becomes a friendly message, via the UNIQUE constraint.
- **Read:** `getSkills` loads the skills and sorts them TEACH-then-LEARN, alphabetically. This uses `Comparator.comparing(Skill::getType).thenComparing(...)`.
- **Update:** `updateSkill` checks that the skill belongs to you, then UPDATEs the name and/or proficiency.
- **Delete:** `deleteSkill` checks ownership, then DELETEs.

### 6.5 SmartMatch engine (Streams + Lambdas + custom functional interface)
`MatchService.findMatches(me, limit)`:
1. Load my skills. `namesOfType(...)` builds **`Set<String> myTeach`** and **`myLearn`** (lower-case names) with `filter` + `map(Skill::normalizedName)` + `collect(toSet())`.
2. Load every other user's skills in one query (`findAllExceptUser`) and **`groupingBy(Skill::getUserId)`** to get `Map<userId, List<Skill>>`.
3. Stream over the groups and run `.map(theirSkills -> buildMatch(...))`. For each user it computes:
   - `theyCanTeachMe` = their TEACH skills that are in my LEARN set (`filter`, `map`, **`distinct`**, `sorted`)
   - `theyWantFromMe` = their LEARN skills that are in my TEACH set
   - `score` = `scorer.score(...)`, a **lambda** of the custom `@FunctionalInterface CompatibilityScorer`
4. `.filter(m -> m.getScore() > 0)` drops incompatible users.
5. `.sorted(Comparator.comparingDouble(Match::getScore).reversed().thenComparing(Match::getUsername))` ranks them.
6. `.limit(limit)` keeps the top N (`match.limit` in config), then `.collect(toList())`.

**Scoring formula (BALANCED lambda):**
`score = 50 × (theyCanTeachMe / skills I want to learn) + 50 × (theyWantFromMe / skills I can teach)`.
A perfect two-way swap scores 100%. A one-way match scores at most 50%. A match is
**MUTUAL** when both lists are non-empty. The menu can show only mutual matches with
`matchService.filter(matches, Match::isMutual)`, a method reference used as a `Predicate`.

**Lazy evaluation:** `map`/`filter`/`sorted` only build the pipeline. Nothing runs until
the terminal operation `collect()`. Because `limit` sits at the end of the pipeline, only
the top N results reach the output list.

Example from the real test run: bob teaches Guitar and learns Java; alice teaches Java
and SQL and learns Guitar, Spanish and Public Speaking. Bob sees *"alice 100.0% [MUTUAL
SWAP], can teach you: Java, wants from you: Guitar"*. From bob's side: alice teaches 1 of
his 1 learn-skills (50) and wants 1 of his 1 teach-skills (50).

From the match list the user picks a match and a skill, enters the hours, and a session request is created.

### 6.6 Session management
State machine (`SessionStatus`):
```
PENDING --teacher accepts--> ACCEPTED --learner marks completed--> COMPLETED (credits move)
PENDING --teacher rejects--> REJECTED
PENDING --learner cancels--> CANCELLED
```
- **Request** (`SessionService.requestSession`) checks:
  - hours are 1..`max.session.hours`
  - the teacher exists and is not yourself
  - the teacher really **teaches** that skill (`findTeachSkillName`, case-insensitive, and the teacher's spelling is stored)
  - the learner can afford it: an early balance check of `cost = hours × credits.per.hour`
  - there is no duplicate open request

  Then it INSERTs the session as PENDING and notifies the teacher (plus a confirmation to the learner).
- **Accept/Reject** (`respondToRequest`): only the teacher, and only while PENDING. It uses a **conditional UPDATE** (`… WHERE session_id=? AND status='PENDING'`). If 0 rows change, someone already acted, so double-acceptance is impossible. The learner is notified.
- **Cancel**: only the learner, only while PENDING.
- **Complete**: only the **learner** can complete, because the learner is the one paying. A teacher can therefore never pay themselves. Completion calls `CreditService.completeSession` (see 6.7).
- **History**: `findByUser` lists sessions where I am the learner or the teacher. They are shown 5 per page with `CollectionUtils.page` (Stream `skip` + `limit`). Incoming, pending and to-complete lists are filtered with lambdas (`Predicate<SkillSession>`).

### 6.7 Credit economy (JDBC transaction, the most important part)
`CreditService.completeSession(sessionId, learnerId)`:
```
try (Connection conn = DBConnection.getConnection()) {
    conn.setAutoCommit(false);                       // start transaction
    try {
        1. SELECT session ... FOR UPDATE              // lock the row; must be ACCEPTED and mine
        2. SELECT credits FROM users ... FOR UPDATE   // lock the learner's wallet
           if balance < cost -> throw "Not enough credits"
        3. UPDATE users SET credits = credits - cost  (learner)
        4. UPDATE users SET credits = credits + cost  (teacher)
        5. INSERT ledger row SESSION_PAYMENT
           UPDATE sessions SET status='COMPLETED' WHERE ... AND status='ACCEPTED'  (must affect 1 row)
        conn.commit();                               // all 5 changes saved together
    } catch (SQLException | ServiceException e) {
        conn.rollback();                             // none of the changes are saved
        throw e;
    } finally { conn.setAutoCommit(true); }
}
```
**Why it can't corrupt wallets:**
- **Atomicity**: all steps happen together or not at all.
- **Row locks** (`FOR UPDATE`) stop two completions running at the same time.
- The **conditional status update** plus **UNIQUE(session_id)** in transactions mean a session can never be paid twice.
- **CHECK (credits >= 0)** is a last line of defence in the database.
- The **ledger is append-only**: the sum of all balances always equals the sum of the welcome bonuses, because transfers only move credits.

*Verified in testing:* after a transfer, alice had 7 and bob had 3, totalling the original 10. In a forced failure (bob's balance lowered to 1 before completing a 3-credit session), the transfer was **rolled back**: balances unchanged, session still ACCEPTED, no ledger row added. Directly running `UPDATE ... credits - 100` in MySQL fails with *"Check constraint 'chk_credits_non_negative' is violated."*

### 6.8 Background notifications (producer-consumer, multithreading)
```
SessionService (main thread)                     NotificationConsumer (background Thread)
   notifyUser() -> queue.produce(n)  ---->  [ NotificationQueue (bounded LinkedList) ]  ---->  queue.consume()
   returns immediately                                                                          -> NotificationDao.insert (own Connection)
                                                                                                -> if recipient logged in: print live alert
```
- `NotificationQueue.produce()`:
  ```
  synchronized (lock) {
      while (queue full && !shutdown) lock.wait();
      queue.addLast(n);
      lock.notifyAll();
  }
  ```
- `consume()`:
  ```
  synchronized (lock) {
      while (queue empty && !shutdown) lock.wait();
      take the first item;
      lock.notifyAll();
  }
  ```
  It returns null only when shut down **and** drained, so no notification is lost on exit.
- **Guarded blocks:** `wait()` is always inside a `while` that re-checks the condition after waking. This guards against spurious wakeups, and against another thread changing the queue first.
- **`wait()` releases the lock** while waiting. `notifyAll()` wakes all waiters, and each re-checks its condition. Both must be called while holding the lock (inside `synchronized`), otherwise Java throws `IllegalMonitorStateException`.
- A **private lock object** is used instead of `this`, so outside code cannot interfere with the monitor.
- **Why a thread?** The database INSERT for every notification runs in the background, so the menu (main thread) never waits for it.
- `activeUserId` is **`volatile`**, so when the main thread logs a user in or out, the consumer sees the change immediately.
- The consumer uses **its own JDBC Connection**, because Connections should not be shared between threads.
- **Shutdown:** `queue.shutdown()` sets a flag and calls `notifyAll()`. The consumer finishes the remaining items and its `run()` ends. The main thread waits with `consumer.join(5000)`.
- Events that notify someone: a session request (to the teacher), accept/reject (to the learner), cancel (to the teacher), and completion (to the teacher, with the credits earned). The actor also gets a confirmation. The inbox (menu 5) shows unread items marked `(*)` and then marks them read. The login screen and main menu show the unread count.

### 6.9 Data import/export and config (file handling)
- **Export** (`DataService` → `CsvExporter.export(path, header, List<? extends CsvExportable>)`): one generic method writes 4 different kinds of file:
  - `exports/<user>_skills.csv`
  - `<user>_sessions.csv`
  - `<user>_transactions.csv`
  - `all_users.csv` (admin; it has **no password hashes**)

  It uses `FileWriter` + `BufferedWriter` in try-with-resources, and escapes commas and quotes.
- **Import** (`CsvImporter.readSkills(path, userId, List<? super Skill>)`): reads with `FileReader` + `BufferedReader` and skips the header and blank lines. Each line is parsed with a quote-aware splitter and validated: 3 columns, TEACH/LEARN, proficiency 1-5, name length. **A malformed line is skipped with its line number. The import continues.** Valid skills are then saved through SkillService, and duplicates are skipped. The test with `sample_data/sample_skills.csv` gave: *"Imported 2 skill(s). Malformed lines skipped: 3. Duplicates skipped: 2."*
- **Backup** (`FileBackupUtil.backup`): copies a file **byte by byte** with `FileInputStream` and `FileOutputStream` (1 KB buffer, until `read()` returns -1) to `exports/backup/<name>_<timestamp>.bak`.
- **Config** (`ConfigLoader`): **StreamTokenizer** with these settings:
  - `commentChar('#')`
  - `quoteChar('"')`
  - `ordinaryChar('/')`, because '/' is a comment character by default
  - `wordChars('_','_')`
  - `eolIsSignificant(true)`

  A small state machine expects `key`, `=`, `value`, end-of-line. Errors such as a missing '=', a line not starting with a key, or extra text after the value are reported with line numbers and skipped. `getInt` falls back to a default if a value is not a number. A missing file means defaults are used.
- **Character vs byte streams:** CSV is text, so Reader/Writer are used (they handle character encoding). A backup must be an exact copy, so InputStream/OutputStream are used (raw bytes).

### 6.10 Admin panel (role-based)
Visible only when `role == ADMIN`, and `AdminService.requireAdmin()` re-checks the role on every call. Features:
- list all users
- **leaderboard top 5 by credits** (`CollectionUtils.topN`, using `User implements Comparable<User>`)
- all transactions and all sessions (paged)
- **delete a user**, after confirmation and never yourself. ON DELETE CASCADE removes their skills, sessions and notifications. Ledger rows keep their history, with the user columns set to NULL.
- export all users to CSV

---

## 7. Syllabus concept → exact location (every file has `// SYLLABUS:` comments)

| Concept | Where |
|---|---|
| **DriverManager** | `DBConnection.getConnection()` |
| **Connection** | Every DAO method; transaction methods receive a shared Connection |
| **PreparedStatement + `?` binding** | All DAOs (`setString`, `setInt`, `setNull`), and DDL in `DatabaseInitializer`. **No plain `Statement` object anywhere** (only the constant `Statement.RETURN_GENERATED_KEYS`) |
| **executeQuery()** | All SELECTs (`findById`, `findAll`, `findByUser` …) |
| **executeUpdate()** | INSERT/UPDATE/DELETE/DDL; the returned row count drives the logic (e.g. conditional status update `== 1`) |
| **ResultSet + typed getters** | `mapRow` in every DAO: `getInt`, `getString`, `getBoolean`, `getTimestamp().toLocalDateTime()`, `wasNull()` |
| **try-with-resources / resource management** | Every Connection, PreparedStatement, ResultSet, FileReader, FileWriter and stream |
| **Generated keys** | `ps.getGeneratedKeys()` in UserDao/SkillDao/SessionDao create |
| **JDBC Transactions** | `CreditService.completeSession` (credit transfer), `AuthService.register` (user + bonus) |
| **Row locking** | `SELECT … FOR UPDATE` in `UserDao.getCreditsForUpdate`, `SessionDao.findByIdForUpdate` |
| **CRUD** | `GenericDao` implemented by UserDao, SkillDao, SessionDao; Skill DNA menu = full CRUD; admin delete = Delete |
| **Custom @FunctionalInterface** | `CompatibilityScorer` |
| **Lambda expressions** | `CompatibilityScorer.BALANCED`, filters in MatchService/SessionService/SkillService/CreditService, `orElseThrow(() -> …)`, `forEach(s -> …)` |
| **Method references** | `Skill::getName`, `Skill::normalizedName`, `Skill::getUserId`, `Match::getScore`, `Match::isMutual`, `System.out::println`, `CsvExporter::escape`, `Number::intValue`, `CreditTransaction::getAmount` |
| **Predicate** | `MatchService.filter(..., Predicate<? super Match>)`, `SessionService.filterHistory(..., Predicate<SkillSession>)` |
| **Stream filter / map** | MatchService, SkillService, CreditService, SessionService |
| **Stream sorted** | MatchService (score desc then name), SkillService, CollectionUtils.topN |
| **Stream distinct** | MatchService.buildMatch |
| **Stream limit** | MatchService (top N), CollectionUtils.topN / page |
| **Stream skip** | CollectionUtils.page (history pagination) |
| **collect / groupingBy / joining / toSet** | MatchService (groupingBy, toSet), CsvExporter (joining) |
| **findFirst + Optional** | `SkillService.findTeachSkillName`; DAOs return `Optional<T>` |
| **Lazy evaluation** | Commented in MatchService: the pipeline runs only at `collect()` |
| **Generic interface** | `GenericDao<T, ID>` |
| **Generic methods** | `CollectionUtils.topN`, `CollectionUtils.page`, `KindredApp.showPaged<T>` |
| **Bounded type parameter** | `<T extends Comparable<? super T>> topN(...)` |
| **Upper bounded wildcard** | `CsvExporter.export(List<? extends CsvExportable>)`, `CollectionUtils.sum(List<? extends Number>)`, `DataService.write(...)` |
| **Lower bounded wildcard** | `CsvImporter.readSkills(..., List<? super Skill>)`, `Predicate<? super Match>`, `Comparable<? super T>` |
| **Diamond operator** | `new ArrayList<>()`, `new HashMap<>()`, `new LinkedList<>()` everywhere |
| **Raw Thread** | `NotificationConsumer extends Thread`, `start()`, `run()`, `join()` |
| **synchronized block** | `NotificationQueue.produce/consume/shutdown` → `synchronized (lock)` |
| **wait() / notifyAll()** | `NotificationQueue` |
| **Guarded blocks (while loops)** | `while (queue.size()==capacity && !shutdown) lock.wait();` and `while (queue.isEmpty() && !shutdown) lock.wait();` |
| **Producer-consumer** | SessionService (producer via NotificationService) → NotificationQueue → NotificationConsumer |
| **volatile** | `NotificationConsumer.activeUserId` |
| **FileReader / FileWriter (character streams)** | CsvImporter / CsvExporter / ConfigLoader |
| **FileInputStream / FileOutputStream (byte streams)** | FileBackupUtil |
| **StreamTokenizer** | ConfigLoader |
| **CSV import/export** | CsvImporter, CsvExporter, DataService |
| **Exception-safe file handling** | try-with-resources and FileNotFoundException/IOException → friendly ServiceException; malformed lines skipped |
| **BCrypt** | PasswordUtil (used by AuthService) |
| **Custom exceptions** | ServiceException (checked), InputClosedException (unchecked) |

---

## 8. Configuration (`config/kindred.conf`)
```
db.url      = "jdbc:mysql://localhost:3306/kindred?createDatabaseIfNotExist=true"
db.user     = "root"
db.password = "CHANGE_ME"          # set your MySQL password here
starting.credits  = 5
credits.per.hour  = 1
max.session.hours = 4
match.limit = 10
notification.queue.capacity = 20
export.dir = "exports"
```
Text with symbols must be quoted, because StreamTokenizer would otherwise split it.
Numbers can be bare. `#` starts a comment, including at the end of a line.

---

## 9. Testing done (real results)
The app was compiled with `build.bat` (no errors) and run end-to-end against a real
MySQL 8.4 server with scripted input:
- Registered alice (became ADMIN, 5 credits) and bob (USER, 5 credits). A username "x" was rejected with a validation message.
- Built Skill DNA by hand and by CSV import. The malformed lines were reported (`MASTER` type, `ten` proficiency, a missing column) and duplicates were skipped.
- Exported skills to CSV and backed the file up (byte copy, 120 bytes).
- SmartMatch for bob: alice at 100% MUTUAL. The request was sent, alice saw it in her inbox, accepted it, and bob completed it. Result: alice 7, bob 3, ledger with 3 rows.
- Requesting a 4-credit session with 3 credits was blocked. A forced failure at completion time was rolled back with no partial changes.
- Admin: user list, leaderboard, all transactions, all-users CSV export (no hashes).
- Wrong password gives "Invalid username or password". A malformed config gives line-numbered warnings while the app still starts. Input ending mid-menu leads to a clean exit.
- Passwords are stored as `$2a$12$…` BCrypt hashes.

---

## 10. Design decisions (why)
- **Raw JDBC, no ORM:** required by the syllabus. It shows exactly how SQL, parameters and ResultSets work.
- **PreparedStatement everywhere:** prevents SQL injection, because user input is bound as data and never concatenated into SQL.
- **The DAO pattern + GenericDao:** one place for SQL, and a consistent CRUD contract with type safety.
- **Transaction methods take a `Connection` parameter:** several DAO calls share one transaction, and the service decides commit or rollback.
- **The first user is ADMIN:** a simple bootstrap with no hard-coded admin password.
- **Only the learner completes a session:** the payer confirms delivery, which prevents teacher self-payment.
- **Credits move at completion, not at request time:** nobody pays for a lesson that never happened. An early balance check at request time gives fast feedback, and the real locked check happens at completion.
- **A one-way match still counts (max 50%):** it is useful to find a teacher even if they want nothing from you, since you pay with credits. Mutual swaps rank higher.
- **Notifications are both stored and printed live:** the console has one user at a time, so the recipient sees stored notifications at next login. The actor sees live confirmation alerts.
- **Tables are auto-created:** the project runs on any machine with MySQL after setting one password.

## 11. Limitations / future improvements
- Single console, one user logged in at a time (no networking). A client-server or web UI could reuse the service layer.
- No connection pool (each call opens a Connection). A pool such as HikariCP would be faster, but the syllabus says raw JDBC.
- Sessions have no scheduled date/time or rating/review system. A rating could be added to the match score.
- Live alerts print over the menu prompt (a console limitation).
- Notification delivery is asynchronous, so a notification appears in the inbox a few milliseconds after the action.

---

## 12. Likely viva questions (short answers)
1. **What is Kindred?** A console time-bank skill exchange: teach to earn SkillCredits, spend them to learn. Java 21, MySQL, raw JDBC.
2. **Explain the architecture.** Layers: app (menus), service (rules), dao (SQL), db (connections), with model and util alongside. Each layer only talks to the one below.
3. **Statement vs PreparedStatement?** PreparedStatement is precompiled with `?` placeholders. Values are bound as data, so SQL injection is impossible, and it is reusable and handles types and NULLs. We never use a plain Statement.
4. **What is try-with-resources?** Resources declared in `try(...)` that implement AutoCloseable are closed automatically, in reverse order, even if an exception occurs. That way no connections or files are leaked.
5. **executeQuery vs executeUpdate?** executeQuery is for SELECT and returns a ResultSet. executeUpdate is for INSERT/UPDATE/DELETE/DDL and returns the number of rows affected. We use that count, for example a conditional status update must return 1.
6. **How do you get the auto-generated id?** `prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)`, then `getGeneratedKeys()`.
7. **Explain your transaction.** `setAutoCommit(false)`, then lock the session and wallet, check the balance, debit, credit, write the ledger, mark COMPLETED, and `commit()`. Any exception leads to `rollback()`. In the `finally` block, auto-commit is turned back on.
8. **What does ACID mean here?** Atomic: all or nothing. Consistent: CHECK credits >= 0, and the totals are conserved. Isolated: `FOR UPDATE` row locks. Durable: committed data survives in MySQL.
9. **What if two people complete the same session at once?** The first locks the row with FOR UPDATE. The second waits, then sees the status COMPLETED and fails. The conditional UPDATE and UNIQUE(session_id) also block double payment.
10. **How are passwords stored?** As BCrypt hashes, salted with cost factor 12. Login uses `checkpw`. The plain password is never stored.
11. **What is a functional interface? Yours?** An interface with exactly one abstract method, so it can be implemented by a lambda. Ours is `CompatibilityScorer`, implemented by the `BALANCED` lambda.
12. **Lambda vs method reference?** A lambda is an inline function, `s -> s.getName()`. A method reference is a shorter form of that when it just calls an existing method, `Skill::getName`.
13. **Explain the SmartMatch pipeline.** groupingBy user, map to Match, filter score > 0, sorted by score desc then name, limit N, collect. Streams are lazy, so nothing runs until collect.
14. **What is lazy evaluation?** Intermediate operations (filter/map/sorted) only describe the work. The terminal operation (collect/forEach) triggers the execution.
15. **Where did you use skip()?** For pagination: `page()` skips `(page-1) × size` items and then limits to `size`.
16. **Why generics?** Type safety at compile time, no casts, and reusable code. For example `GenericDao<User,Integer>.findById` returns `Optional<User>`.
17. **`? extends` vs `? super`?** PECS: Producer Extends, Consumer Super. The exporter reads from the list, so it takes `List<? extends CsvExportable>`. The importer writes into the list, so it takes `List<? super Skill>`.
18. **Bounded type parameter example?** `<T extends Comparable<? super T>> List<T> topN(List<T>, int)`. T must be comparable, which lets the leaderboard sort Users by credits.
19. **Explain producer-consumer in your project.** Services (producers, main thread) put notifications into a bounded queue. The consumer thread takes them, saves them to MySQL and shows alerts. When the queue is full, the producer waits. When it is empty, the consumer waits. `notifyAll` wakes the other side.
20. **Why `while` and not `if` around wait()?** Spurious wakeups, and another thread may grab the item first. The condition must be re-checked after waking.
21. **wait() vs sleep()?** wait releases the lock and needs synchronized and notify. sleep keeps any lock and just pauses.
22. **Why notifyAll instead of notify?** Producers and consumers wait on the same lock. notify might wake the wrong kind of thread and stall. notifyAll is safe because everyone re-checks.
23. **Why is activeUserId volatile?** It is written by the main thread and read by the consumer thread. volatile guarantees visibility across threads.
24. **How does the thread stop?** `shutdown()` sets a flag and calls `notifyAll`. The consumer drains the remaining items, `consume()` returns null, and `run()` ends. Main calls `join()`.
25. **Byte vs character streams?** Byte streams (FileInputStream/FileOutputStream) handle raw bytes, used for the exact backup copy. Character streams (FileReader/FileWriter) handle text with encoding, used for CSV.
26. **What is StreamTokenizer?** It splits a character stream into tokens (words, numbers, quoted strings, EOL). We use it to parse `key = value` config lines, and we handle malformed lines.
27. **How do you handle a malformed CSV?** Each line is parsed in a try/catch. A bad line is reported with its line number and skipped, and the rest are imported.
28. **How is role-based access done?** Role ENUM in users. The first user is ADMIN. The admin menu is only shown to admins, and AdminService re-checks the role on every call.
29. **What happens when a user is deleted?** FK ON DELETE CASCADE removes their skills, sessions and notifications. Ledger rows use SET NULL, so the financial history remains.
30. **How does the app avoid crashing?** InputHelper re-asks for invalid numbers. All service errors become ServiceException messages. File and DB errors are caught. End of input leads to a graceful exit. The MySQL constraints back up the Java validation.
