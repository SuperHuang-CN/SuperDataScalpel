package cn.superhuang.data.scalpel.dialect.query;

/** A physical column projected under a public result alias. */
public record QueryProjection(String column, String alias) {

    public QueryProjection {
        if (column == null || column.isBlank() || alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Projection column and alias are required");
        }
    }
}
