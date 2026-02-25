package org.anay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableAspectJAutoProxy // This enables the @Aspect logic we wrote
@EnableTransactionManagement(order = 2) // Transaction starts AFTER the Aspect
public class DbMasterReadPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMasterReadPocApplication.class, args);
    }
}