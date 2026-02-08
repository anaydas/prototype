package demo;

import config.DatabaseConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates database connections WITHOUT pooling.
 * Each thread creates a new connection for every query.
 * 
 * This is INEFFICIENT because:
 * - Creating a connection is expensive (TCP handshake, authentication, etc.)
 * - Each thread creates its own connection
 * - Connections are not reused
 */
public class NonPoolConnection {

    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    public static long execute() {
        System.out.println("\n" + repeatChar('=', 50));
        System.out.println("  NON-POOLED CONNECTION TEST");
        System.out.println("  Each thread creates NEW connection for each query");
        System.out.println(repeatChar('=', 50));

        int threadCount = DatabaseConfig.THREAD_COUNT;

        CountDownLatch latch = new CountDownLatch(threadCount);
        successCount.set(0);
        failCount.set(0);

        long startTime = System.currentTimeMillis();

        // Spawn multiple threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i + 1;

            new Thread(() -> {
                try {

                    // Each query creates a NEW connection - THIS IS INEFFICIENT!
                    executeQueryWithNewConnection(threadId);

                } finally {
                    latch.countDown();
                }
            }, "NonPool-Thread-" + threadId).start();
        }

        // Wait for all threads to complete
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("\n--- Non-Pooled Results ---");
        System.out.println("Total Threads: " + threadCount);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + failCount.get());
        System.out.println("TOTAL TIME: " + totalTime + " ms");

        return totalTime;
    }

    private static void executeQueryWithNewConnection(int threadId) {
        Connection connection = null;

        try {
            // Create a NEW connection for this query
            connection = DriverManager.getConnection(
                    DatabaseConfig.JDBC_URL,
                    DatabaseConfig.USERNAME,
                    DatabaseConfig.PASSWORD);

            // Execute simple SELECT query
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM users");

            if (rs.next()) {
                int count = rs.getInt("cnt");
                // System.out.println("[NonPool] Thread-" + threadId +
                // " | New Connection | Users count: " + count);
            }

            rs.close();
            stmt.close();
            successCount.incrementAndGet();

        } catch (Exception e) {
            System.err.println("[NonPool] Thread-" + threadId + " Error: " + e.getMessage());
            failCount.incrementAndGet();
        } finally {
            // Close the connection after each query
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    // Java 8 compatible string repeat helper
    private static String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
