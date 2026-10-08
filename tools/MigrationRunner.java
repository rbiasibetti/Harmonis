import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Runner minimalista de scripts SQL para SQL Server (sem Flyway).
 * Uso: java MigrationRunner <jdbcUrl> <user> <pass> <arquivo.sql> [arquivo2.sql ...]
 * Separa batches por linhas contendo apenas "GO".
 */
public class MigrationRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Uso: MigrationRunner <jdbcUrl> <user> <pass> <arquivo.sql>...");
            System.exit(2);
        }
        String url = args[0], user = args[1], pass = args[2];
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(true);
            for (int i = 3; i < args.length; i++) {
                Path file = Paths.get(args[i]);
                String sql = Files.readString(file);
                System.out.println("== Aplicando " + file.getFileName() + " ==");
                for (String batch : splitOnGo(sql)) {
                    String trimmed = batch.strip();
                    if (trimmed.isEmpty()) continue;
                    try (Statement st = conn.createStatement()) {
                        st.execute(trimmed);
                    }
                }
            }
        }
        System.out.println("OK");
    }

    private static List<String> splitOnGo(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (line.strip().equalsIgnoreCase("GO")) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(line).append('\n');
            }
        }
        out.add(cur.toString());
        return out;
    }
}
