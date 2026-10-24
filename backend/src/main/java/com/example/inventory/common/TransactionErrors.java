package com.example.inventory.common;

import com.mongodb.MongoException;
import org.springframework.dao.TransientDataAccessException;

/** Spring Data 4.3 may wrap Mongo write conflicts in UncategorizedMongoDbException. */
public final class TransactionErrors {
    private TransactionErrors() { }

    public static boolean isTransient(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException) return true;
            if (cause instanceof MongoException mongo && mongo.hasErrorLabel("TransientTransactionError")) return true;
        }
        return false;
    }
}
