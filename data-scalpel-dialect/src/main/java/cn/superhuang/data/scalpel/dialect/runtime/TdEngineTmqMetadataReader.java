package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DatabaseMetadataProvider;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the server-owned TMQ topic catalog and accepts only complete-super-table data topics.
 * It is intentionally a restricted recognizer, not a general SQL parser.
 */
public final class TdEngineTmqMetadataReader {

    private static final Pattern COMPLETE_SUPERTABLE_QUERY = Pattern.compile(
            "(?is)^\\s*select\\s+\\*\\s+from\\s+(`[^`]+`|[a-zA-Z_][a-zA-Z0-9_$]*)"
                    + "\\s*\\.\\s*(`[^`]+`|[a-zA-Z_][a-zA-Z0-9_$]*)\\s*;?\\s*$"
    );
    private static final Set<String> TRUE_VALUES = Set.of("1", "true", "yes", "on");

    private final DatabaseDialect dialect;
    private final DatabaseMetadataProvider metadataProvider;

    public TdEngineTmqMetadataReader(DatabaseDialect dialect) {
        if (!(dialect instanceof DatabaseMetadataProvider provider)) {
            throw new IllegalArgumentException("TDengine 方言未提供元数据读取能力");
        }
        this.dialect = dialect;
        this.metadataProvider = provider;
    }

    public List<TdEngineTmqTopic> list(Connection connection, String keyword) throws SQLException {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<TdEngineTmqTopic> topics = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM information_schema.ins_topics")) {
            Map<String, Integer> columns = columns(resultSet.getMetaData());
            int topicColumn = requiredColumn(columns, "topic_name", "topic");
            int databaseColumn = optionalColumn(columns, "db_name", "database_name", "database");
            int createdAtColumn = optionalColumn(columns, "create_time", "created_at");
            int sqlColumn = optionalColumn(columns, "sql", "topic_sql", "definition");
            int metaColumn = optionalColumn(columns, "with_meta", "meta");
            // Current TDengine ins_topics exposes no subtype column. Keep these aliases for a
            // compatible future server, but interpret the official numeric subtype correctly:
            // DB=1, TABLE=2, COLUMN(query)=3.
            int typeColumn = optionalColumn(columns, "topic_type", "sub_type", "type");
            int idColumn = optionalColumn(columns, "topic_id", "uid", "id");
            while (resultSet.next()) {
                String topicName = text(resultSet, topicColumn);
                if (topicName == null || (!normalizedKeyword.isEmpty()
                        && !topicName.toLowerCase(Locale.ROOT).contains(normalizedKeyword))) {
                    continue;
                }
                topics.add(readTopic(
                        connection,
                        topicName,
                        text(resultSet, databaseColumn),
                        instant(resultSet, createdAtColumn),
                        text(resultSet, sqlColumn),
                        text(resultSet, metaColumn),
                        text(resultSet, typeColumn),
                        text(resultSet, idColumn)
                ));
            }
        }
        topics.sort(java.util.Comparator.comparing(TdEngineTmqTopic::topicName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(topics);
    }

    private TdEngineTmqTopic readTopic(
            Connection connection,
            String topicName,
            String catalogDatabase,
            Instant createdAt,
            String definition,
            String withMeta,
            String topicType,
            String topicId
    ) {
        String normalizedDefinition = normalizeDefinition(definition);
        Matcher matcher = normalizedDefinition == null
                ? COMPLETE_SUPERTABLE_QUERY.matcher("")
                : COMPLETE_SUPERTABLE_QUERY.matcher(normalizedDefinition);
        String database = null;
        String supertable = null;
        String reason = null;
        TableMetadata tableMetadata = null;
        String timePrecision = null;

        if (isTrue(withMeta) || containsMetaModifier(definition)) {
            reason = "包含元数据消息的 Topic 暂不支持";
        } else if (topicType != null && !isDataTopicType(topicType)) {
            reason = "仅支持 SELECT * 查询型超级表 Topic";
        } else if (!matcher.matches()) {
            reason = definition == null || definition.isBlank()
                    ? "数据库 Topic 或缺少查询定义的 Topic 暂不支持"
                    : "仅支持 SELECT * FROM database.supertable 形式的完整超级表 Topic";
        } else {
            database = unquote(matcher.group(1));
            supertable = unquote(matcher.group(2));
            if (catalogDatabase != null && !catalogDatabase.equalsIgnoreCase(database)) {
                reason = "Topic 数据库与查询来源不一致";
            } else {
                try {
                    tableMetadata = metadataProvider.readTableMetadata(
                            connection, new TableIdentifier(database, null, supertable));
                    for (var column : tableMetadata.columns()) {
                        var mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(column));
                        if (!mapping.acceptable() || mapping.definition() == null) {
                            reason = "超级表字段无法无损映射：" + column.name();
                            tableMetadata = null;
                            break;
                        }
                    }
                    if (reason == null) {
                        timePrecision = timestampPrecision(tableMetadata);
                    }
                } catch (DatabaseAccessException | SQLException exception) {
                    reason = "来源对象不存在、不是超级表或结构暂不受支持";
                    tableMetadata = null;
                }
            }
        }

        String fingerprint = fingerprint(
                normalizedTopicType(topicType),
                database == null ? catalogDatabase : database,
                supertable,
                normalizedDefinition,
                topicId,
                createdAt
        );
        return new TdEngineTmqTopic(
                topicName,
                database == null ? catalogDatabase : database,
                supertable,
                createdAt,
                reason == null,
                reason,
                fingerprint,
                timePrecision,
                tableMetadata
        );
    }

    private static Map<String, Integer> columns(ResultSetMetaData metadata) throws SQLException {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String label = metadata.getColumnLabel(index);
            if (label != null) {
                columns.putIfAbsent(label.toLowerCase(Locale.ROOT), index);
            }
            String name = metadata.getColumnName(index);
            if (name != null) {
                columns.putIfAbsent(name.toLowerCase(Locale.ROOT), index);
            }
        }
        return columns;
    }

    private static int requiredColumn(Map<String, Integer> columns, String... candidates) throws SQLException {
        int result = optionalColumn(columns, candidates);
        if (result < 1) {
            throw new SQLException("TDengine Topic 元数据缺少 Topic 名称字段");
        }
        return result;
    }

    private static int optionalColumn(Map<String, Integer> columns, String... candidates) {
        for (String candidate : candidates) {
            Integer index = columns.get(candidate);
            if (index != null) return index;
        }
        return -1;
    }

    private static String text(ResultSet resultSet, int column) throws SQLException {
        if (column < 1) return null;
        Object value = resultSet.getObject(column);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static Instant instant(ResultSet resultSet, int column) throws SQLException {
        if (column < 1) return null;
        Object value = resultSet.getObject(column);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    private static String normalizeDefinition(String definition) {
        if (definition == null || definition.isBlank()) return null;
        String normalized = definition.trim().replaceAll("\\s+", " ");
        int asIndex = normalized.toLowerCase(Locale.ROOT).indexOf(" as select ");
        if (normalized.toLowerCase(Locale.ROOT).startsWith("create topic ") && asIndex >= 0) {
            normalized = normalized.substring(asIndex + 4);
        }
        return normalized;
    }

    private static boolean containsMetaModifier(String definition) {
        return definition != null && definition.toLowerCase(Locale.ROOT).matches("(?s).*\\bwith\\s+meta\\b.*");
    }

    private static boolean isTrue(String value) {
        return value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    static boolean isDataTopicType(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals("query") || normalized.equals("data")
                || normalized.equals("select") || normalized.equals("column") || normalized.equals("3");
    }

    private static String normalizedTopicType(String value) {
        return value == null || isDataTopicType(value)
                ? "COLUMN"
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String unquote(String identifier) {
        String value = identifier.trim();
        return value.startsWith("`") ? value.substring(1, value.length() - 1) : value;
    }

    private static String timestampPrecision(TableMetadata metadata) {
        return metadata.columns().stream()
                .map(column -> column.nativeType().toUpperCase(Locale.ROOT))
                .filter(type -> type.startsWith("TIMESTAMP"))
                .map(type -> type.contains("(US)") ? "US" : type.contains("(NS)") ? "NS" : "MS")
                .findFirst()
                .orElse(null);
    }

    private static String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                String normalized = value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
                digest.update(normalized.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
