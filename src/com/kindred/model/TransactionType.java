package com.kindred.model;

/**
 * Kinds of rows in the credit ledger (transactions table).
 * WELCOME_BONUS   -> starting credits given when an account is created
 * SESSION_PAYMENT -> credits moved from learner to teacher after a completed session
 */
// SYLLABUS: JDBC Transactions - both kinds of ledger row are written inside a transaction
public enum TransactionType {
    WELCOME_BONUS,
    SESSION_PAYMENT
}
