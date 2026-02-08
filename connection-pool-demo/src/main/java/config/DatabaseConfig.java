package config;

/**
 * Database Configuration
 * 
 * ============================================
 * CONFIGURE YOUR DATABASE SETTINGS HERE
 * ============================================
 */
public class DatabaseConfig {

    // JDBC URL - format: jdbc:mysql://host:port/database
    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/prototype";

    // Database username
    public static final String USERNAME = "devuser1";

    // Database password
    public static final String PASSWORD = "MyNewPass1!";

    // Schema/Database name (already included in JDBC_URL)
    public static final String SCHEMA = "prototype";

    // Connection pool settings
    public static final int POOL_SIZE = 5; // Number of connections in pool
    public static final int THREAD_COUNT = 1000; // Number of threads to spawn
}
