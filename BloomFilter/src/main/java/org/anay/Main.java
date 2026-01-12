package org.anay;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        List<String> db_usernames = new ArrayList<>();
        for(int i=0; i<1000; i++){
            db_usernames.add("user_"+i);
        }

        ScalableBloomFilter bloomFilter = new ScalableBloomFilter(500,0.01);

        System.out.println("Populating the bloom filter");
        db_usernames.forEach(bloomFilter::add);

        bloomFilter.printStats(db_usernames);

        //Test users
        /*String testUser1 = "user_450";
        String testUser2 = "user_1015";
        String testUser3 = "guest_001";

        System.out.println("Contains '"+testUser1+ "' : "+ bloomFilter.mightContain(testUser1));
        System.out.println("Contains '"+testUser2+ "' : "+ bloomFilter.mightContain(testUser2));
        System.out.println("Contains '"+testUser3+ "' : "+ bloomFilter.mightContain(testUser3));*/
    }
}