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

    private static String removeOptionalTerminalSemicolon(String sql) {
        String source = sql.trim();
        ScanState state = ScanState.CODE;
        int semicolon = -1;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '\'') state = ScanState.SINGLE_QUOTE;
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
            }
        }
        if (state == ScanState.SINGLE_QUOTE || state == ScanState.DOUBLE_QUOTE || state == ScanState.BACKTICK
                || state == ScanState.BRACKET || state == ScanState.BLOCK_COMMENT) {
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
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '\'') {
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
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("SQL contains unclosed parentheses");
        }
        return List.copyOf(tokens);
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private enum ScanState {
        CODE, SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK, BRACKET, LINE_COMMENT, BLOCK_COMMENT
    }

    private record Token(String text, int start, int depth) {
    }
}
