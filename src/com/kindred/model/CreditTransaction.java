package com.kindred.model;

import java.time.LocalDateTime;

/**
 * One row of the credit ledger (`transactions` table).
 *
 * The ledger is APPEND-ONLY: rows are inserted, never updated or deleted, so
 * the history of every credit movement can always be checked.
 *
 * Nullable columns use Integer (not int) so they can hold null:
 *   - fromUserId is null for a WELCOME_BONUS (credits come from the system)
 *   - sessionId  is null for a WELCOME_BONUS
 */
// SYLLABUS: JDBC Transactions - a ledger row is inserted inside the credit-transfer transaction
public class CreditTransaction implements CsvExportable {

    public static final String CSV_HEADER = "txn_id,type,from_user,to_user,amount,session_id,created_at";

    private int txnId;
    private Integer sessionId;
    private Integer fromUserId;
    private String fromUsername;
    private Integer toUserId;
    private String toUsername;
    private int amount;
    private TransactionType type;
    private LocalDateTime createdAt;

    /** A new ledger entry that is not saved yet. */
    public CreditTransaction(Integer sessionId, Integer fromUserId, Integer toUserId,
                             int amount, TransactionType type) {
        this.sessionId = sessionId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.type = type;
    }

    /** A ledger entry loaded from the database (usernames come from JOINs). */
    public CreditTransaction(int txnId, Integer sessionId, Integer fromUserId, String fromUsername,
                             Integer toUserId, String toUsername, int amount,
                             TransactionType type, LocalDateTime createdAt) {
        this(sessionId, fromUserId, toUserId, amount, type);
        this.txnId = txnId;
        this.fromUsername = fromUsername;
        this.toUsername = toUsername;
        this.createdAt = createdAt;
    }

    /** Factory method for the starting credits of a new account. */
    public static CreditTransaction welcomeBonus(int userId, int amount) {
        return new CreditTransaction(null, null, userId, amount, TransactionType.WELCOME_BONUS);
    }

    public int getTxnId()            { return txnId; }
    public Integer getSessionId()    { return sessionId; }
    public Integer getFromUserId()   { return fromUserId; }
    public String getFromUsername()  { return fromUsername; }
    public Integer getToUserId()     { return toUserId; }
    public String getToUsername()    { return toUsername; }
    public int getAmount()           { return amount; }
    public TransactionType getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    private static String nameOrSystem(String name) {
        return name == null ? "SYSTEM" : name;
    }

    @Override
    public String[] csvValues() {
        return new String[] {
                String.valueOf(txnId), type.name(), nameOrSystem(fromUsername), nameOrSystem(toUsername),
                String.valueOf(amount), sessionId == null ? "" : String.valueOf(sessionId),
                String.valueOf(createdAt)
        };
    }

    @Override
    public String toString() {
        return String.format("TXN #%-4d %-15s %-12s -> %-12s %3d credits  %s",
                txnId, type, nameOrSystem(fromUsername), nameOrSystem(toUsername), amount,
                sessionId == null ? "" : "(session #" + sessionId + ")");
    }
}
