package campus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.sqlite.SQLiteConfig;

/** Opens SQLite connections (location from DATABASE_PATH) and creates the schema on first run. */
public final class DatabaseConnection {
    private static final String DEFAULT_PATH = "data/campus_events.db";

    private DatabaseConnection() {}

    public static String path() {
        String env = System.getenv("DATABASE_PATH");
        return (env == null || env.isBlank()) ? DEFAULT_PATH : env.trim();
    }

    public static Connection get() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection("jdbc:sqlite:" + path(), config.toProperties());
    }

    /** Creates the database file/folder if missing and applies schema.sql. Safe to call repeatedly. */
    public static synchronized void initialize() throws SQLException, IOException {
        Path parent = Paths.get(path()).toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (Connection c = get(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            for (String sql : loadSchemaStatements()) {
                st.execute(sql);
            }
            migrateStudentColumns(c, st);
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_users_roll_no ON users (roll_no) WHERE roll_no IS NOT NULL");
        }
    }

    /** Databases created before the class / year / roll-number fields existed get the columns added. */
    private static void migrateStudentColumns(Connection c, Statement st) throws SQLException {
        java.util.Set<String> cols = new java.util.HashSet<>();
        try (java.sql.ResultSet rs = c.createStatement().executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) cols.add(rs.getString("name"));
        }
        if (!cols.contains("student_class")) st.execute("ALTER TABLE users ADD COLUMN student_class TEXT");
        if (!cols.contains("passing_year")) st.execute("ALTER TABLE users ADD COLUMN passing_year INTEGER");
        if (!cols.contains("roll_no")) st.execute("ALTER TABLE users ADD COLUMN roll_no TEXT");
    }

    private static String[] loadSchemaStatements() throws IOException {
        try (InputStream in = DatabaseConnection.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on the classpath");
            StringBuilder cleaned = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().startsWith("--")) cleaned.append(line).append('\n');
            }
            return java.util.Arrays.stream(cleaned.toString().split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
    }
}
