package cn.superhuang.data.scalpel.dialect.query;

/**
 * One whitelisted logical field exposed to a standard table query.
 * The physical column is supplied by trusted model metadata, never by a client request.
 */
public record StandardQueryField(
        String code,
        String physicalColumn,
        QueryValueType valueType,
        boolean primaryKey,
        boolean queryable
) {

    public StandardQueryField {
        if (code == null || code.isBlank() || physicalColumn == null || physicalColumn.isBlank()) {
            throw new IllegalArgumentException("Field code and physical column are required");
        }
        if (queryable && valueType == null) {
            throw new IllegalArgumentException("Queryable field value type is required");
        }
    }
}
