package com.example.inventory.common;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.transaction.TransactionSystemException;
import static org.junit.jupiter.api.Assertions.*;

class TransactionErrorsTest {
    @Test
    void recognisesLabelEvenWhenSpringCannotCategoriseConflict() {
        MongoException conflict = new MongoException(112, "WriteConflict");
        conflict.addLabel("TransientTransactionError");
        assertTrue(TransactionErrors.isTransient(new UncategorizedMongoDbException("wrapped", conflict)));
    }

    @Test
    void unknownCommitIsNotSafeToBlindlyReplayAsAnAbortedTransaction() {
        MongoException uncertain = new MongoException("commit acknowledgement lost");
        uncertain.addLabel("UnknownTransactionCommitResult");
        assertFalse(TransactionErrors.isTransient(new TransactionSystemException("commit failed", uncertain)));
    }
}
