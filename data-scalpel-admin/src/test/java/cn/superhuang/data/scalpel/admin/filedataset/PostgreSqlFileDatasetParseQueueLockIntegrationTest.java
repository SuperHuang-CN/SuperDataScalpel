package cn.superhuang.data.scalpel.admin.filedataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in proof of the production PostgreSQL SKIP LOCKED semantics used by the queue repository. */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_PG_INTEGRATION", matches = "(?i)true")
class PostgreSqlFileDatasetParseQueueLockIntegrationTest {

    @Test
    void skipsTheLockedFirstJobAndReturnsTheNextCandidate() throws Exception {
        String schema = validatedIdentifier(
                System.getenv().getOrDefault("DATASCALPEL_PG_SCHEMA", "datascalpel_task_test")
        );
        String table = "ds_file_parse_queue_" + UUID.randomUUID().toString().replace("-", "");
        String qualifiedTable = quote(schema) + "." + quote(table);
        try (Connection setup = connection(); Statement statement = setup.createStatement()) {
            statement.execute("create schema if not exists " + quote(schema));
            statement.execute("""
                    create table %s (
                        id uuid primary key,
                        status varchar(32) not null,
                        available_at timestamp with time zone not null,
                        created_at timestamp with time zone not null
                    )
                    """.formatted(qualifiedTable));
            try (PreparedStatement insert = setup.prepareStatement(
                    "insert into " + qualifiedTable + " (id, status, available_at, created_at) values (?, ?, ?, ?)"
            )) {
                insertJob(insert, UUID.fromString("00000000-0000-0000-0000-000000000001"), Instant.now().minusSeconds(2));
                insertJob(insert, UUID.fromString("00000000-0000-0000-0000-000000000002"), Instant.now().minusSeconds(1));
            }
        }

        try (Connection first = connection(); Connection second = connection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            UUID firstJob = claim(first, qualifiedTable);
            UUID secondJob = claim(second, qualifiedTable);

            assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), firstJob);
            assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), secondJob);
            first.rollback();
            second.rollback();
        } finally {
            try (Connection cleanup = connection(); Statement statement = cleanup.createStatement()) {
                statement.execute("drop table if exists " + qualifiedTable);
            }
        }
    }

    private static UUID claim(Connection connection, String qualifiedTable) throws SQLException {
        String sql = """
                select job.id
                from %s job
                where job.status = 'QUEUED'
                  and job.available_at <= ?
                order by job.available_at, job.created_at, job.id
                limit 1
                for update skip locked
                """.formatted(qualifiedTable);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, Instant.now());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("PostgreSQL 未返回可领取任务");
                }
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private static void insertJob(PreparedStatement insert, UUID id, Instant time) throws SQLException {
        insert.setObject(1, id);
        insert.setString(2, "QUEUED");
        insert.setObject(3, time);
        insert.setObject(4, time);
        insert.executeUpdate();
    }

    private static Connection connection() throws SQLException {
        String host = requiredEnvironment("DATASCALPEL_PG_HOST");
        String port = System.getenv().getOrDefault("DATASCALPEL_PG_PORT", "5432");
        String database = requiredEnvironment("DATASCALPEL_PG_DATABASE");
        String sslMode = System.getenv().getOrDefault("DATASCALPEL_PG_SSLMODE", "disable");
        Properties properties = new Properties();
        properties.setProperty("user", requiredEnvironment("DATASCALPEL_PG_USERNAME"));
        properties.setProperty("password", requiredEnvironment("DATASCALPEL_PG_PASSWORD"));
        return DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=" + sslMode,
                properties
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 PostgreSQL 集成测试环境变量：" + name);
        }
        return value.trim();
    }

    private static String validatedIdentifier(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("PostgreSQL 测试 Schema 名称无效");
        }
        return normalized;
    }

    private static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }
}
