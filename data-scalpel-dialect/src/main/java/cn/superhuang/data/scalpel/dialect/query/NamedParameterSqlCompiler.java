package cn.superhuang.data.scalpel.dialect.query;

import java.util.ArrayList;
import java.util.List;

/** Compiles value-only {@code :name} placeholders without interpreting quoted SQL text. */
public final class NamedParameterSqlCompiler {

    private static final int MAX_SQL_LENGTH = 100_000;
    private static final int MAX_BINDINGS = 200;

    private NamedParameterSqlCompiler() {
    }

    public static SqlTemplateCompilation compile(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required");
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("SQL length must not exceed " + MAX_SQL_LENGTH + " characters");
        }
        StringBuilder jdbcSql = new StringBuilder(sql.length());
        List<String> bindings = new ArrayList<>();
        State state = State.CODE;
        String dollarDelimiter = null;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    String delimiter = dollarDelimiterAt(sql, index);
                    if (delimiter != null) {
                        state = State.DOLLAR_QUOTE;
                        dollarDelimiter = delimiter;
                        jdbcSql.append(delimiter);
                        index += delimiter.length() - 1;
                    } else if (current == '\'') {
                        state = State.SINGLE_QUOTE;
                        jdbcSql.append(current);
                    } else if (current == '"') {
                        state = State.DOUBLE_QUOTE;
                        jdbcSql.append(current);
                    } else if (current == '`') {
                        state = State.BACKTICK;
                        jdbcSql.append(current);
                    } else if (current == '[') {
                        state = State.BRACKET;
                        jdbcSql.append(current);
                    } else if (current == '-' && next == '-') {
                        state = State.LINE_COMMENT;
                        jdbcSql.append(current).append(next);
                        index++;
                    } else if (current == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        jdbcSql.append(current).append(next);
                        index++;
                    } else if (current == '$' && next == '{') {
                        throw new IllegalArgumentException("SQL text substitution is not supported");
                    } else if (current == ':' && next != ':'
                            && (index == 0 || sql.charAt(index - 1) != ':')
                            && isIdentifierStart(next)) {
                        int end = index + 2;
                        while (end < sql.length() && isIdentifierPart(sql.charAt(end))) end++;
                        bindings.add(sql.substring(index + 1, end));
                        if (bindings.size() > MAX_BINDINGS) {
                            throw new IllegalArgumentException("SQL contains more than " + MAX_BINDINGS + " parameter bindings");
                        }
                        jdbcSql.append('?');
                        index = end - 1;
                    } else {
                        jdbcSql.append(current);
                    }
                }
                case SINGLE_QUOTE -> {
                    jdbcSql.append(current);
                    if (current == '\'' && next == '\'') {
                        jdbcSql.append(next);
                        index++;
                    } else if (current == '\'') {
                        state = State.CODE;
                    }
                }
                case DOUBLE_QUOTE -> {
                    jdbcSql.append(current);
                    if (current == '"' && next == '"') {
                        jdbcSql.append(next);
                        index++;
                    } else if (current == '"') {
                        state = State.CODE;
                    }
                }
                case BACKTICK -> {
                    jdbcSql.append(current);
                    if (current == '`' && next == '`') {
                        jdbcSql.append(next);
                        index++;
                    } else if (current == '`') {
                        state = State.CODE;
                    }
                }
                case BRACKET -> {
                    jdbcSql.append(current);
                    if (current == ']') state = State.CODE;
                }
                case LINE_COMMENT -> {
                    jdbcSql.append(current);
                    if (current == '\n' || current == '\r') state = State.CODE;
                }
                case BLOCK_COMMENT -> {
                    jdbcSql.append(current);
                    if (current == '*' && next == '/') {
                        jdbcSql.append(next);
                        index++;
                        state = State.CODE;
                    }
                }
                case DOLLAR_QUOTE -> {
                    if (sql.startsWith(dollarDelimiter, index)) {
                        jdbcSql.append(dollarDelimiter);
                        index += dollarDelimiter.length() - 1;
                        dollarDelimiter = null;
                        state = State.CODE;
                    } else {
                        jdbcSql.append(current);
                    }
                }
            }
        }
        if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE || state == State.BACKTICK
                || state == State.BRACKET || state == State.BLOCK_COMMENT || state == State.DOLLAR_QUOTE) {
            throw new IllegalArgumentException("SQL contains an unclosed quoted value or comment");
        }
        return new SqlTemplateCompilation(jdbcSql.toString().trim(), bindings);
    }

    /** Counts JDBC value placeholders while ignoring quoted text, identifiers and comments. */
    public static int countJdbcPlaceholders(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required");
        }
        State state = State.CODE;
        String dollarDelimiter = null;
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    String delimiter = dollarDelimiterAt(sql, index);
                    if (delimiter != null) {
                        state = State.DOLLAR_QUOTE;
                        dollarDelimiter = delimiter;
                        index += delimiter.length() - 1;
                    } else if (current == '\'') {
                        state = State.SINGLE_QUOTE;
                    } else if (current == '"') {
                        state = State.DOUBLE_QUOTE;
                    } else if (current == '`') {
                        state = State.BACKTICK;
                    } else if (current == '[') {
                        state = State.BRACKET;
                    } else if (current == '-' && next == '-') {
                        state = State.LINE_COMMENT;
                        index++;
                    } else if (current == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        index++;
                    } else if (current == '?') {
                        count++;
                    }
                }
                case SINGLE_QUOTE -> {
                    if (current == '\'' && next == '\'') index++;
                    else if (current == '\'') state = State.CODE;
                }
                case DOUBLE_QUOTE -> {
                    if (current == '"' && next == '"') index++;
                    else if (current == '"') state = State.CODE;
                }
                case BACKTICK -> {
                    if (current == '`' && next == '`') index++;
                    else if (current == '`') state = State.CODE;
                }
                case BRACKET -> {
                    if (current == ']') state = State.CODE;
                }
                case LINE_COMMENT -> {
                    if (current == '\n' || current == '\r') state = State.CODE;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = State.CODE;
                        index++;
                    }
                }
                case DOLLAR_QUOTE -> {
                    if (sql.startsWith(dollarDelimiter, index)) {
                        index += dollarDelimiter.length() - 1;
                        dollarDelimiter = null;
                        state = State.CODE;
                    }
                }
            }
        }
        if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE || state == State.BACKTICK
                || state == State.BRACKET || state == State.BLOCK_COMMENT || state == State.DOLLAR_QUOTE) {
            throw new IllegalArgumentException("SQL contains an unclosed quoted value or comment");
        }
        return count;
    }

    private static String dollarDelimiterAt(String source, int index) {
        if (source.charAt(index) != '$') return null;
        int end = source.indexOf('$', index + 1);
        if (end < 0) return null;
        String tag = source.substring(index + 1, end);
        if (!tag.isEmpty() && (!isIdentifierStart(tag.charAt(0))
                || tag.chars().skip(1).anyMatch(value -> !isIdentifierPart((char) value)))) {
            return null;
        }
        return source.substring(index, end + 1);
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private enum State {
        CODE, SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK, BRACKET, LINE_COMMENT, BLOCK_COMMENT, DOLLAR_QUOTE
    }
}
