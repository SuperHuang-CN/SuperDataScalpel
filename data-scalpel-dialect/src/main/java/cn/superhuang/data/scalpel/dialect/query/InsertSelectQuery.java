package cn.superhuang.data.scalpel.dialect.query;

/**
 * A single read-only SELECT query split around its optional common-table-expression prefix.
 * The split lets dialects place a WITH clause where their INSERT grammar requires it.
 */
public record InsertSelectQuery(String withClause, String selectSql, String normalizedSql) {

    public InsertSelectQuery(String withClause, String selectSql) {
        this(withClause, selectSql, withClause == null || withClause.isBlank()
                ? selectSql
                : withClause + " " + selectSql);
    }

    public InsertSelectQuery {
        withClause = normalizeOptional(withClause);
        if (selectSql == null || selectSql.isBlank()) {
            throw new IllegalArgumentException("SELECT query is required");
        }
        selectSql = selectSql.trim();
        if (!startsWithKeyword(selectSql, "SELECT")) {
            throw new IllegalArgumentException("Query must begin with SELECT");
        }
        if (withClause != null && !startsWithKeyword(withClause, "WITH")) {
            throw new IllegalArgumentException("WITH clause must begin with WITH");
        }
        if (normalizedSql == null || normalizedSql.isBlank()) {
            throw new IllegalArgumentException("Normalized SQL is required");
        }
        normalizedSql = normalizedSql.trim();
    }

    public String sql() {
        return normalizedSql;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean startsWithKeyword(String value, String keyword) {
        int start = firstCodeIndex(value);
        if (start < 0 || !value.regionMatches(true, start, keyword, 0, keyword.length())) {
            return false;
        }
        int after = start + keyword.length();
        return value.length() == after || !Character.isLetterOrDigit(value.charAt(after))
                && value.charAt(after) != '_';
    }

    private static int firstCodeIndex(String value) {
        int index = 0;
        while (index < value.length()) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
            if (index + 1 < value.length() && value.charAt(index) == '-' && value.charAt(index + 1) == '-') {
                index += 2;
                while (index < value.length() && value.charAt(index) != '\n' && value.charAt(index) != '\r') index++;
                continue;
            }
            if (index + 1 < value.length() && value.charAt(index) == '/' && value.charAt(index + 1) == '*') {
                int end = value.indexOf("*/", index + 2);
                if (end < 0) return -1;
                index = end + 2;
                continue;
            }
            return index;
        }
        return -1;
    }
}
