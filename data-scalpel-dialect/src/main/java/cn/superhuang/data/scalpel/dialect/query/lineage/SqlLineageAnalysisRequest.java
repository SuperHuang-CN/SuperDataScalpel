package cn.superhuang.data.scalpel.dialect.query.lineage;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;

/** Framework-independent input catalog for static SQL lineage analysis. */
public record SqlLineageAnalysisRequest(
        String databaseType,
        String sql,
        String defaultCatalog,
        String defaultSchema,
        List<Relation> relations
) {
    public SqlLineageAnalysisRequest {
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public record Relation(
            String relationKey,
            TableIdentifier table,
            List<Field> fields
    ) {
        public Relation {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record Field(String fieldKey, String columnName, int ordinal) {
    }
}
