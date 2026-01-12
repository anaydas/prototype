package org.anay;

import java.util.ArrayList;
import java.util.List;

public class ScalableBloomFilter {

    private final List<SimpleBloomFilter> filters = new ArrayList<>();
    private final int initialCapacity;
    private final double fpp;
    private int currentElementCount = 0;

    public ScalableBloomFilter(int initialCapacity, double fpp){
        this.initialCapacity = initialCapacity;
        this.fpp = fpp;
        addNewFilter();
    }

    private void addNewFilter(){
        filters.add(new SimpleBloomFilter(initialCapacity,fpp));
    }

    public void add(String username){
        if(filters.get(filters.size()-1).isFull())
            addNewFilter();
        filters.get(filters.size()-1).add(username);
        currentElementCount++;
    }

    public boolean mightContain(String username){
        return filters.stream().anyMatch(filter->filter.mightContain(username));
    }

    public void printStats(List<String> realDatabase) {
        int totalItems = filters.stream().mapToInt(SimpleBloomFilter::getInsertedCount).sum();

        // To calculate False Positives, we check names we KNOW are NOT in the DB
        int falsePositives = 0;
        int tests = 10000;
        for (int i = 0; i < tests; i++) {
            String fakeUser = "non_existent_user_" + i;
            // If the filter says YES, but we know it's NOT in the DB, it's a False Positive
            if (this.mightContain(fakeUser)) {
                falsePositives++;
            }
        }

        System.out.println("--- Bloom Filter Statistics ---");
        System.out.println("Total Layers (Filters): " + filters.size());
        System.out.println("Total Usernames Added:  " + totalItems);
        System.out.println("False Positive Count:   " + falsePositives + " (out of " + tests + " random tests)");
        System.out.printf("Actual Error Rate:      %.2f%%\n", (falsePositives / (double) tests) * 100);
        System.out.println("-------------------------------");
    }

}
