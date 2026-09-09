package cn.superhuang.data.scalpel.contract.task;

import java.util.Locale;
import java.util.Set;

/** Pure lexical safety boundary for FILTER SQL predicates. Spark Analyzer remains authoritative. */
public final class FilterSqlExpressionPolicy {

    public static final int MAX_EXPRESSION_LENGTH = 8_192;

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "WHERE", "SELECT", "FROM", "JOIN", "UNION", "WITH",
            "INSERT", "UPDATE", "DELETE", "MERGE", "CREATE", "ALTER",
            "DROP", "TRUNCATE"
    );

    private FilterSqlExpressionPolicy() {
    }

    public static Violation findViolation(String expression) {
        return findViolation(expression, Set.of());
    }

    public static Violation findViolation(String expression, Set<String> additionalForbiddenKeywords) {
        if (expression == null || expression.isBlank()) {
            return Violation.REQUIRED;
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            return Violation.TOO_LONG;
        }

        StringBuilder unquoted = new StringBuilder(expression.length());
        char quote = 0;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (quote != 0) {
                unquoted.append(' ');
                if (current == '\\' && index + 1 < expression.length()) {
                    unquoted.append(' ');
                    index++;
                    continue;
                }
                if (current == quote) {
                    if (index + 1 < expression.length() && expression.charAt(index + 1) == quote) {
                        unquoted.append(' ');
                        index++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                unquoted.append(' ');
                continue;
            }
            if (current == ';') {
                return Violation.STATEMENT_SEPARATOR;
            }
            if (index + 1 < expression.length()) {
                char next = expression.charAt(index + 1);
                if ((current == '-' && next == '-')
                        || (current == '/' && next == '*')
                        || (current == '*' && next == '/')) {
                    return Violation.COMMENT;
                }
            }
            unquoted.append(current);
        }

        String value = unquoted.toString().toUpperCase(Locale.ROOT);
        StringBuilder token = new StringBuilder();
        for (int index = 0; index <= value.length(); index++) {
            char current = index < value.length() ? value.charAt(index) : ' ';
            if (Character.isLetterOrDigit(current) || current == '_') {
                token.append(current);
            } else if (!token.isEmpty()) {
                if (FORBIDDEN_KEYWORDS.contains(token.toString()) || additionalForbiddenKeywords.contains(token.toString())) {
                    return Violation.STATEMENT_KEYWORD;
                }
                token.setLength(0);
            }
        }
        return null;
    }

    public enum Violation {
        REQUIRED,
        TOO_LONG,
        STATEMENT_KEYWORD,
        STATEMENT_SEPARATOR,
        COMMENT
    }
}
