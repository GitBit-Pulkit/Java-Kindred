# Kindred: Peer-to-Peer Skill Exchange (Java 21 + MySQL, console)

Teach what you know, earn **SkillCredits**, and spend them to learn from someone else.
It is a time-bank economy with no money involved.

**Stack:** Java 21, MySQL 8, raw JDBC, BCrypt. No Maven, Spring or Hibernate.
The two JARs are already in `lib/`:
- `mysql-connector-j-9.1.0.jar`
- `jbcrypt-0.4.jar`

## Setup (one time)

### Teammate Setup:
1. Clone the repository.
2. Copy `config/kindred.example.conf`.
3. Rename the copy to `kindred.conf` (in the `config/` folder).
4. Enter your own local MySQL credentials:
   ```properties
   db.password = "your_mysql_root_password"
   ```
5. Ensure MySQL Server is running.
6. Run `build.bat`.
7. Run `run.bat`.

The database `kindred` and all tables are created automatically on first start.
`sql/schema.sql` holds the same schema for reference.

## Run

Double-click **`run.bat`**. It compiles and starts the app. To do it by hand:

```
build.bat
java -cp "out;lib\*" com.kindred.app.KindredApp
```

Run it from the `kindred` folder so `config/` and `exports/` are found.

## First steps

1. Register. **The first account becomes the ADMIN.** Every account starts with 5 credits.
2. Register a second account. Give each account TEACH and LEARN skills that complement the other's.
3. Log in as the learner and open **SmartMatch**. Pick a match to request a session.
4. Log in as the teacher. Go to **Sessions** and then **Incoming requests**, and accept.
5. Log in as the learner. Go to **Sessions**, then **Mark completed**. The credits move in one JDBC transaction.

`sample_data/sample_skills.csv` is a sample import file. It deliberately contains some malformed lines, to show error handling.

See **`PROJECT_BRIEF.md`** for the full explanation (viva preparation).
