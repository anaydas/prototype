package pool;

import config.DatabaseConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Simple Connection Pool using BlockingQueue.
 * 
 * HOW IT WORKS:
 * - Pre-creates a fixed number of connections at startup
 * - Stores connections in a thread-safe BlockingQueue
 * - getConnection() blocks if no connection available (waits for one to be
 * released)
 * - releaseConnection() returns the connection back to the pool
 * 
 * THREAD SAFETY:
 * - ArrayBlockingQueue is thread-safe
 * - take() and put() are blocking operations
 * - Multiple threads can safely get/release connections concurrently
 */
public class ConnectionPool {

    private final BlockingQueue<Connection> pool;
    private final int poolSize;
    private boolean isShutdown = false;

    /**
     * Creates a connection pool with the specified size.
     * Pre-creates all connections upfront.
     */
    public ConnectionPool(int poolSize) throws SQLException {
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);

        System.out.println("[Pool] Initializing connection pool with " + poolSize + " connections...");

        // Pre-create connections
        for (int i = 0; i < poolSize; i++) {
            Connection conn = createConnection();
            pool.offer(conn);
            System.out.println("[Pool] Created connection " + (i + 1) + "/" + poolSize);
        }

        System.out.println("[Pool] Connection pool ready!");
    }

    /**
     * Get a connection from the pool.
     * BLOCKS if no connection is available (waits until one is released).
     */
    public Connection getConnection() throws InterruptedException {
        if (isShutdown) {
            throw new IllegalStateException("Connection pool is shutdown");
        }

        // take() blocks until a connection is available
        Connection conn = pool.take();
        return conn;
    }

    /**
     * Return a connection back to the pool.
     * This makes the connection available for other threads.
     */
    public void releaseConnection(Connection connection) {
        if (connection != null && !isShutdown) {
            try {
                // Validate connection before returning to pool
                if (!connection.isClosed()) {
                    pool.offer(connection);
                } else {
                    // Connection is closed, create a new one
                    pool.offer(createConnection());
                }
            } catch (SQLException e) {
                // Connection is broken, try to create a new one
                try {
                    pool.offer(createConnection());
                } catch (SQLException ex) {
                    System.err.println("[Pool] Failed to replace broken connection: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Shutdown the pool and close all connections.
     */
    public void shutdown() {
        isShutdown = true;
        System.out.println("[Pool] Shutting down connection pool...");

        while (!pool.isEmpty()) {
            try {
                Connection conn = pool.poll();
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                // Ignore errors during shutdown
            }
        }

        System.out.println("[Pool] Connection pool shutdown complete.");
    }

    /**
     * Get current available connections in the pool.
     */
    public int getAvailableConnections() {
        return pool.size();
    }

    /**
     * Get the total pool size.
     */
    public int getPoolSize() {
        return poolSize;
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(
                DatabaseConfig.JDBC_URL,
                DatabaseConfig.USERNAME,
                DatabaseConfig.PASSWORD);
    }
}
