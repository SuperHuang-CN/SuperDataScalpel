package cn.superhuang.data.scalpel.dialect.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DialectRegistry {

    private final Map<String, DatabaseDialect> dialects;

    public DialectRegistry(Collection<? extends DatabaseDialect> dialects) {
        Map<String, DatabaseDialect> registered = new LinkedHashMap<>();
        for (DatabaseDialect dialect : dialects) {
            String id = normalize(dialect.definition().id());
            if (registered.putIfAbsent(id, dialect) != null) {
                throw new IllegalArgumentException("Duplicate database dialect: " + id);
            }
        }
        this.dialects = Map.copyOf(registered);
    }

    public DatabaseDialect require(String id) {
        DatabaseDialect dialect = dialects.get(normalize(id));
        if (dialect == null) {
            throw new IllegalArgumentException("Unsupported database type: " + id);
        }
        return dialect;
    }

    public List<DatabaseDialect> all() {
        return dialects.values().stream()
                .sorted((left, right) -> left.definition().displayName().compareTo(right.definition().displayName()))
                .toList();
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Database type is required");
        }
        return id.trim().toUpperCase(Locale.ROOT);
    }
}
