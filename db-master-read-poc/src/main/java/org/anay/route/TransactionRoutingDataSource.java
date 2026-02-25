package org.anay.route;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TransactionRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        Object key = DbContextHolder.get();
        System.out.println("DEBUG: RoutingDataSource is selecting: " + (key != null ? key : "DEFAULT"));
        return key;
    }
}