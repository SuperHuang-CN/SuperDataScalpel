package cn.superhuang.data.scalpel.dialect.query;

/** Data and optional count statements for one SQL service request. */
public record CompiledSqlServiceQuery(PreparedSqlQuery dataQuery, PreparedSqlQuery countQuery) {

    public CompiledSqlServiceQuery {
        if (dataQuery == null) {
            throw new IllegalArgumentException("SQL service data query is required");
        }
    }
}
