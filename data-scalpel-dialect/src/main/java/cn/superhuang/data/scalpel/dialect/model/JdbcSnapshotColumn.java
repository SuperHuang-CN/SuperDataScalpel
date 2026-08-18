package cn.superhuang.data.scalpel.dialect.model;

/** Trusted target-column metadata used to render snapshot synchronization SQL. */
public record JdbcSnapshotColumn(
        String name,
        Integer geometrySpatialReferenceId
) {
    public JdbcSnapshotColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Snapshot column name is required");
        }
        if (geometrySpatialReferenceId != null && geometrySpatialReferenceId < 0) {
            throw new IllegalArgumentException("Geometry SRID must be non-negative");
        }
    }

    public boolean geometry() {
        return geometrySpatialReferenceId != null;
    }
}
