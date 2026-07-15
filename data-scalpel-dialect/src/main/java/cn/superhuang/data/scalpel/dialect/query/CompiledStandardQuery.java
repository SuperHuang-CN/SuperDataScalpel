package cn.superhuang.data.scalpel.dialect.query;

/** Compiled data query and optional count query for one standard-service request. */
public record CompiledStandardQuery(PreparedQuery dataQuery, PreparedQuery countQuery) {

    public CompiledStandardQuery {
        if (dataQuery == null) {
            throw new IllegalArgumentException("Data query is required");
        }
    }
}
