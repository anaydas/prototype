import config.DatabaseConfig;
import demo.NonPoolConnection;
import demo.PooledConnection;

/**
 * Main class to demonstrate and compare:
 * 1. Non-Pooled Connections - Each query creates a new connection
 * 2. Pooled Connections - Connections are reused from a pool
 * 
 * Run this after starting MySQL with docker-compose:
 * docker-compose up -d
 * mvn compile exec:java -Dexec.mainClass="Main"
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("\n" + repeatChar('=', 60));
        System.out.println("   DATABASE CONNECTION POOLING DEMO");
        System.out.println(repeatChar('=', 60));

        System.out.println("\nConfiguration:");
        System.out.println("  JDBC URL: " + DatabaseConfig.JDBC_URL);
        System.out.println("  Username: " + DatabaseConfig.USERNAME);
        System.out.println("  Threads: " + DatabaseConfig.THREAD_COUNT);
        System.out.println("  Pool Size: " + DatabaseConfig.POOL_SIZE);

        // Load MySQL driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found. Make sure mysql-connector-java is in classpath.");
            System.exit(1);
        }

        /*
         * // Run Non-Pooled test
         * long nonPooledTime = NonPoolConnection.execute();
         * 
         * // Small pause between tests
         * try {
         * Thread.sleep(1000);
         * } catch (InterruptedException e) {
         * Thread.currentThread().interrupt();
         * }
         * 
         */

        // long nonPooledTime = NonPoolConnection.execute();

        // Run Pooled test
        long pooledTime = PooledConnection.execute();

        // Print comparison
        // printComparison(nonPooledTime, pooledTime);

        // Cleanup MySQL driver's background thread to avoid shutdown warnings
        try {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private static void printComparison(long nonPooledTime, long pooledTime) {
        System.out.println("\n" + repeatChar('=', 60));
        System.out.println("                    COMPARISON SUMMARY");
        System.out.println(repeatChar('=', 60));

        System.out.println("\n  +---------------------+--------------+");
        System.out.println("  |     Method          |   Time (ms)  |");
        System.out.println("  +---------------------+--------------+");
        System.out.printf("  | Non-Pooled          | %10d   |%n", nonPooledTime);
        System.out.printf("  | Pooled              | %10d   |%n", pooledTime);
        System.out.println("  +---------------------+--------------+");

        if (pooledTime > 0 && nonPooledTime > 0) {
            double improvement = ((double) (nonPooledTime - pooledTime) / nonPooledTime) * 100;
            double speedup = (double) nonPooledTime / pooledTime;

            System.out.println("\n  RESULT:");
            if (improvement > 0) {
                System.out.printf("  * Pooled connections were %.1f%% faster%n", improvement);
                System.out.printf("  * That's %.2fx speedup!%n", speedup);
            } else {
                System.out.printf("  Note: Non-pooled was faster by %.1f%% (unusual for production loads)%n",
                        -improvement);
            }
        }

        System.out.println("\n  WHY IS POOLED FASTER?");
        System.out.println("  * Non-Pooled: Creates NEW connection for EACH query");
        System.out.println("    - TCP handshake every time");
        System.out.println("    - Authentication every time");
        System.out.println("    - Connection setup overhead every time");
        System.out.println();
        System.out.println("  * Pooled: REUSES existing connections");
        System.out.println("    - Connections created once at startup");
        System.out.println("    - BlockingQueue handles thread-safe sharing");
        System.out.println("    - Near-zero overhead for getting a connection");

        System.out.println("\n" + repeatChar('=', 60) + "\n");
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
