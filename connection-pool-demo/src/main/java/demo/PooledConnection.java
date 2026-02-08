package demo;

import config.DatabaseConfig;
import pool.ConnectionPool;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates database connections WITH pooling.
 * Threads share a pool of pre-created connections.
 * 
 * This is EFFICIENT because:
 * - Connections are created once and reused
 * - No overhead of creating new connections for each query
 * - BlockingQueue handles thread coordination automatically
 */
public class PooledConnection {

    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    public static long execute() {
        System.out.println("\n" + repeatChar('=', 50));
        System.out.println("  POOLED CONNECTION TEST");
        System.out.println("  Threads share " + DatabaseConfig.POOL_SIZE + " pre-created connections");
        System.out.println(repeatChar('=', 50));

        int threadCount = DatabaseConfig.THREAD_COUNT;

        CountDownLatch latch = new CountDownLatch(threadCount);
        successCount.set(0);
        failCount.set(0);

        ConnectionPool pool = null;

        try {
            // Create the connection pool (connections created upfront)
            pool = new ConnectionPool(DatabaseConfig.POOL_SIZE);

            long startTime = System.currentTimeMillis();
            final ConnectionPool finalPool = pool;

            // Spawn multiple threads
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i + 1;

                new Thread(() -> {
                    try {

                        // Get connection from pool - EFFICIENT!
                        executeQueryWithPooledConnection(finalPool, threadId);

                    } finally {
                        latch.countDown();
                    }
                }, "Pooled-Thread-" + threadId).start();
            }

            // Wait for all threads to complete
            latch.await();

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            System.out.println("\n--- Pooled Results ---");
            System.out.println("Total Threads: " + threadCount);
            System.out.println("Pool Size: " + DatabaseConfig.POOL_SIZE);
            System.out.println("Successful: " + successCount.get());
            System.out.println("Failed: " + failCount.get());
            System.out.println("TOTAL TIME: " + totalTime + " ms");

            return totalTime;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return -1;
        } finally {
            if (pool != null) {
                pool.shutdown();
            }
        }
    }

    private static void executeQueryWithPooledConnection(ConnectionPool pool, int threadId) {
        Connection connection = null;

        try {
            // Get connection from pool (may block if all connections are in use)
            connection = pool.getConnection();

            // Execute simple SELECT query
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM users");

            if (rs.next()) {
                int count = rs.getInt("cnt");
                // System.out.println("[Pooled] Thread-" + threadId +
                // " | Reused Connection | Users count: " + count);
            }

            rs.close();
            stmt.close();
            successCount.incrementAndGet();

        } catch (Exception e) {
            System.err.println("[Pooled] Thread-" + threadId + " Error: " + e.getMessage());
            failCount.incrementAndGet();
        } finally {
            // Return connection to the pool (NOT closing it!)
            if (connection != null) {
                pool.releaseConnection(connection);
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
