package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight lexical guard for user-authored local SQL tasks.
 * It deliberately accepts only one SELECT or WITH ... SELECT statement and does not try to be a full SQL parser.
 */
public final class ReadOnlySelectQueryParser {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "ALTER", "CALL", "COMMIT", "CREATE", "DELETE", "DROP", "EXEC", "EXECUTE", "GRANT", "INSERT",
            "MERGE", "REVOKE", "ROLLBACK", "SET", "TRUNCATE", "UPDATE", "USE", "INTO"
    );

    private ReadOnlySelectQueryParser() {
    }

    public static InsertSelectQuery parse(String sql) {
        return parse(sql, false);
    }

    /** SQL-service variant that reserves pagination and locking clauses for the runtime wrapper. */
    public static InsertSelectQuery parseServiceQuery(String sql) {
        return parse(sql, true);
    }

    private static InsertSelectQuery parse(String sql, boolean serviceQuery) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required");
        }
        String statement = removeOptionalTerminalSemicolon(sql);
        List<Token> tokens = scanTokens(statement);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("SQL must contain a SELECT query");
        }
        for (Token token : tokens) {
            if (FORBIDDEN_KEYWORDS.contains(token.text())) {
                throw new IllegalArgumentException("Only read-only SELECT queries are allowed; forbidden keyword: " + token.text());
            }
        }
        if (serviceQuery) {
            validateServiceTokens(tokens);
        }

        Token first = tokens.getFirst();
        if ("SELECT".equals(first.text())) {
            return new InsertSelectQuery(null, statement, statement);
        }
        if (!"WITH".equals(first.text())) {
            throw new IllegalArgumentException("SQL must begin with SELECT or WITH");
        }

        Token finalSelect = tokens.stream()
                .filter(token -> token.depth() == 0 && "SELECT".equals(token.text()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WITH query must end with a top-level SELECT"));
        return new InsertSelectQuery(
                statement.substring(0, finalSelect.start()),
                statement.substring(finalSelect.start()),
                statement
        );
    }

    private static void validateServiceTokens(List<Token> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.depth() == 0 && Set.of("LIMIT", "OFFSET", "FETCH").contains(token.text())) {
                throw new IllegalArgumentException("SQL service pagination is controlled by the Service Engine; forbidden keyword: " + token.text());
            }
            if (token.depth() == 0 && "FOR".equals(token.text()) && index + 1 < tokens.size()) {
                Token next = tokens.get(index + 1);
                if (next.depth() == 0 && ("SHARE".equals(next.text()) || "KEY".equals(next.text()) || "NO".equals(next.text()))) {
                    throw new IllegalArgumentException("SQL service locking clauses are not allowed");
                }
            }
        }
    }

    private static String removeOptionalTerminalSemicolon(String sql) {
        String source = sql.trim();
        ScanState state = ScanState.CODE;
        String dollarDelimiter = null;
        int semicolon = -1;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    String delimiter = dollarDelimiterAt(source, index);
                    if (delimiter != null) {
                        state = ScanState.DOLLAR_QUOTE;
                        dollarDelimiter = delimiter;
                        index += delimiter.length() - 1;
                    }
                    else if (current == '\'') state = ScanState.SINGLE_QUOTE;
                    else if (current == '"') state = ScanState.DOUBLE_QUOTE;
                    else if (current == '`') state = ScanState.BACKTICK;
                    else if (current == '[') state = ScanState.BRACKET;
                    else if (current == '-' && next == '-') {
                        state = ScanState.LINE_COMMENT;
                        index++;
                    } else if (current == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT;
                        index++;
                    } else if (current == ';') {
                        if (semicolon >= 0 || !onlyIgnorable(source, index + 1)) {
                            throw new IllegalArgumentException("Only one SQL statement is allowed");
                        }
                        semicolon = index;
                    }
                }
                case SINGLE_QUOTE -> {
                    if (current == '\'' && next == '\'') index++;
                    else if (current == '\'') state = ScanState.CODE;
                }
                case DOUBLE_QUOTE -> {
                    if (current == '"' && next == '"') index++;
                    else if (current == '"') state = ScanState.CODE;
                }
                case BACKTICK -> {
                    if (current == '`' && next == '`') index++;
                    else if (current == '`') state = ScanState.CODE;
                }
                case BRACKET -> {
                    if (current == ']') state = ScanState.CODE;
                }
                case LINE_COMMENT -> {
                    if (current == '\n' || current == '\r') state = ScanState.CODE;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = ScanState.CODE;
                        index++;
                    }
                }
                case DOLLAR_QUOTE -> {
                    if (source.startsWith(dollarDelimiter, index)) {
                        index += dollarDelimiter.length() - 1;
                        dollarDelimiter = null;
                        state = ScanState.CODE;
                    }
                }
            }
        }
        if (state == ScanState.SINGLE_QUOTE || state == ScanState.DOUBLE_QUOTE || state == ScanState.BACKTICK
                || state == ScanState.BRACKET || state == ScanState.BLOCK_COMMENT || state == ScanState.DOLLAR_QUOTE) {
            throw new IllegalArgumentException("SQL contains an unclosed quoted value or comment");
        }
        return (semicolon >= 0 ? source.substring(0, semicolon) : source).trim();
    }

    private static boolean onlyIgnorable(String source, int start) {
        ScanState state = ScanState.CODE;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (Character.isWhitespace(current)) continue;
                    if (current == '-' && next == '-') {
                        state = ScanState.LINE_COMMENT;
                        index++;
                        continue;
                    }
                    if (current == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT;
                        index++;
                        continue;
                    }
                    return false;
                }
                case LINE_COMMENT -> {
                    if (current == '\n' || current == '\r') state = ScanState.CODE;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = ScanState.CODE;
                        index++;
                    }
                }
                default -> throw new IllegalStateException("Unexpected quote state after a terminal semicolon");
            }
        }
        return state == ScanState.CODE || state == ScanState.LINE_COMMENT;
    }

    private static List<Token> scanTokens(String source) {
        java.util.ArrayList<Token> tokens = new java.util.ArrayList<>();
        ScanState state = ScanState.CODE;
        String dollarDelimiter = null;
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    String delimiter = dollarDelimiterAt(source, index);
                    if (delimiter != null) {
                        state = ScanState.DOLLAR_QUOTE;
                        dollarDelimiter = delimiter;
                        index += delimiter.length() - 1;
                    } else if (current == '\'') {
                        state = ScanState.SINGLE_QUOTE;
                    } else if (current == '"') {
                        state = ScanState.DOUBLE_QUOTE;
                    } else if (current == '`') {
                        state = ScanState.BACKTICK;
                    } else if (current == '[') {
                        state = ScanState.BRACKET;
                    } else if (current == '-' && next == '-') {
                        state = ScanState.LINE_COMMENT;
                        index++;
                    } else if (current == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT;
                        index++;
                    } else if (current == '(') {
                        depth++;
                    } else if (current == ')') {
                        if (depth == 0) {
                            throw new IllegalArgumentException("SQL contains an unmatched closing parenthesis");
                        }
                        depth--;
                    } else if (isIdentifierStart(current)) {
                        int start = index;
                        while (index + 1 < source.length() && isIdentifierPart(source.charAt(index + 1))) index++;
                        tokens.add(new Token(source.substring(start, index + 1).toUpperCase(Locale.ROOT), start, depth));
                    }
                }
                case SINGLE_QUOTE -> {
                    if (current == '\'' && next == '\'') index++;
                    else if (current == '\'') state = ScanState.CODE;
                }
                case DOUBLE_QUOTE -> {
                    if (current == '"' && next == '"') index++;
                    else if (current == '"') state = ScanState.CODE;
                }
                case BACKTICK -> {
                    if (current == '`' && next == '`') index++;
                    else if (current == '`') state = ScanState.CODE;
                }
                case BRACKET -> {
                    if (current == ']') state = ScanState.CODE;
                }
                case LINE_COMMENT -> {
                    if (current == '\n' || current == '\r') state = ScanState.CODE;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = ScanState.CODE;
                        index++;
                    }
                }
                case DOLLAR_QUOTE -> {
                    if (source.startsWith(dollarDelimiter, index)) {
                        index += dollarDelimiter.length() - 1;
                        dollarDelimiter = null;
                        state = ScanState.CODE;
                    }
                }
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("SQL contains unclosed parentheses");
        }
        if (state == ScanState.SINGLE_QUOTE || state == ScanState.DOUBLE_QUOTE || state == ScanState.BACKTICK
                || state == ScanState.BRACKET || state == ScanState.BLOCK_COMMENT || state == ScanState.DOLLAR_QUOTE) {
            throw new IllegalArgumentException("SQL contains an unclosed quoted value or comment");
        }
        return List.copyOf(tokens);
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
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private enum ScanState {
        CODE, SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK, BRACKET, LINE_COMMENT, BLOCK_COMMENT, DOLLAR_QUOTE
    }

    private record Token(String text, int start, int depth) {
    }
}
