package cn.superhuang.data.scalpel.dialect.model;

/** One trusted target column in a prepared row UPSERT. Geometry columns bind WKB. */
public record JdbcUpsertColumn(String name, Integer geometrySpatialReferenceId) {
}
