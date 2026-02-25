package org.anay.route;

import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@Order(1) // Force this to run BEFORE the Transaction Manager
public class DataSourceAspect {
    // Intercept methods with @Transactional annotation
    @Before("@annotation(transactional)")
    public void setDataSource(Transactional transactional) {
        if (transactional.readOnly()) {
            System.out.println("Routing to: REPLICA");
            DbContextHolder.set("REPLICA");
        } else {
            System.out.println("Routing to: MASTER");
            DbContextHolder.set("MASTER");
        }
    }

    @After("@annotation(transactional)")
    public void clear() {
        DbContextHolder.clear();
    }
}