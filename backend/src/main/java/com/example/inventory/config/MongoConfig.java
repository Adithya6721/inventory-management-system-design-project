package com.example.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.TransactionOptions;

@Configuration
@EnableTransactionManagement
public class MongoConfig {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build());
    }

    @Bean
    ApplicationRunner requireTransactionalMongo(MongoTemplate template) {
        return args -> {
            var hello = template.executeCommand("{ hello: 1 }");
            if (!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) {
                throw new IllegalStateException("Inventory writes require MongoDB transactions. "
                        + "Start a replica set; see docs/consistency.md. Standalone mode is unsupported.");
            }
        };
    }
}
