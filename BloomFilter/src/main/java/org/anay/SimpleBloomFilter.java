package org.anay;

import java.util.BitSet;
import java.util.Objects;

public class SimpleBloomFilter {

    private final int capacity;
    private final BitSet bits;
    private int insertedCount = 0;
    private final int bitSetSize;
    private final int numberOfHashFunctions;

    // ffp stands for false positive probability. if it is 0.01 that mean 1% of all check can be false positive
    public SimpleBloomFilter(int capacity, double fpp) {
        this.capacity = capacity;
        // Formula: m = -(n * ln(p)) / (ln(2)^2)
        this.bitSetSize = (int) (-(capacity * Math.log(fpp)) / (Math.pow(Math.log(2), 2)));
        // Formula: k = (m / n) * ln(2)
        this.numberOfHashFunctions = Math.max(1, (int) Math.round((double) bitSetSize / capacity * Math.log(2)));
        this.bits = new BitSet(bitSetSize);
    }

    public void add(String data){
        for(int i=0;i<numberOfHashFunctions;i++){
            bits.set(getHash(data,i));
        }
        insertedCount++;
    }

    public boolean mightContain(String data){
        for(int i=0;i<numberOfHashFunctions;i++){
            if(!bits.get(getHash(data,i))){
                return false;
            }

        }
        return true;
    }

    public boolean isFull(){
        return insertedCount>=capacity;
    }

    /*public int getHash(String data, int seed){
        int hash = Objects.hash(data,seed);
        return Math.abs(hash%bitSetSize);
    }*/

    private int getHash(String data, int index) {
        // Generate two different base hashes
        int hash1 = data.hashCode();
        int hash2 = (hash1 >>> 16) ^ (hash1 * 0x85ebca6b); // A simple "mixer"

        // Combine them using the index: hash = hash1 + (index * hash2)
        // This is a standard way to simulate multiple independent hash functions
        int combinedHash = hash1 + (index * hash2);

        return Math.abs(combinedHash % bitSetSize);
    }


    public int getInsertedCount() {
        return insertedCount;
    }



}
