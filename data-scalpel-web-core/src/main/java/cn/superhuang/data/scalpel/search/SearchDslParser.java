package cn.superhuang.data.scalpel.search;

/** Parser for the compact entity-search DSL exposed by {@link SearchEngine}. */
final class SearchDslParser {

    private final String input;
    private int position;

    private SearchDslParser(String input) {
        this.input = input;
    }

    static SearchNode parse(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        SearchDslParser parser = new SearchDslParser(search);
        SearchNode node = parser.parseOr();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.invalid("unexpected token at position " + parser.position);
        }
        return node;
    }

    private SearchNode parseOr() {
        SearchNode node = parseAnd();
        while (consumeKeyword("OR")) {
            node = new LogicalNode(LogicalOperator.OR, node, parseAnd());
        }
        return node;
    }

    private SearchNode parseAnd() {
        SearchNode node = parsePrimary();
        while (consumeKeyword("AND")) {
            node = new LogicalNode(LogicalOperator.AND, node, parsePrimary());
        }
        return node;
    }

    private SearchNode parsePrimary() {
        skipWhitespace();
        if (atEnd()) {
            throw invalid("expected a condition");
        }
        if (peek() == '(') {
            position++;
            SearchNode expression = parseOr();
            skipWhitespace();
            if (atEnd() || peek() != ')') {
                throw invalid("missing closing parenthesis");
            }
            position++;
            return expression;
        }
        if (peek() == ')') {
            throw invalid("unexpected closing parenthesis");
        }
        return parseCondition();
    }

    private ConditionNode parseCondition() {
        String field = readField();
        SearchOperator operator = readOperator();
        skipWhitespace();

        if (startsNullLiteral()) {
            position += 4;
            return switch (operator) {
                case EQUAL -> new ConditionNode(field, SearchOperator.IS_NULL, null);
                case NOT_EQUAL -> new ConditionNode(field, SearchOperator.IS_NOT_NULL, null);
                default -> throw invalid("null is only supported with : or !");
            };
        }

        boolean leadingWildcard = consume('*');
        String value = readQuotedValue();
        boolean trailingWildcard = consume('*');
        if ((leadingWildcard || trailingWildcard) && operator != SearchOperator.EQUAL) {
            throw invalid("wildcards are only supported with :");
        }

        SearchOperator resolvedOperator = operator;
        if (leadingWildcard && trailingWildcard) {
            resolvedOperator = SearchOperator.CONTAINS;
        } else if (leadingWildcard) {
            resolvedOperator = SearchOperator.ENDS_WITH;
        } else if (trailingWildcard) {
            resolvedOperator = SearchOperator.STARTS_WITH;
        }
        return new ConditionNode(field, resolvedOperator, value);
    }

    private String readField() {
        if (atEnd() || !isIdentifierStart(peek())) {
            throw invalid("expected a field at position " + position);
        }
        int start = position++;
        while (!atEnd() && isIdentifierPart(peek())) {
            position++;
        }
        return input.substring(start, position);
    }

    private SearchOperator readOperator() {
        if (atEnd()) {
            throw invalid("missing operator");
        }
        if (consume(">=")) return SearchOperator.GREATER_THAN_OR_EQUAL;
        if (consume("<=")) return SearchOperator.LESS_THAN_OR_EQUAL;
        char symbol = input.charAt(position++);
        return switch (symbol) {
            case ':' -> SearchOperator.EQUAL;
            case '!' -> SearchOperator.NOT_EQUAL;
            case '>' -> SearchOperator.GREATER_THAN;
            case '<' -> SearchOperator.LESS_THAN;
            default -> throw invalid("unsupported operator at position " + (position - 1));
        };
    }

    private String readQuotedValue() {
        if (atEnd() || peek() != '"') {
            throw invalid("values must be enclosed in double quotes");
        }
        position++;
        StringBuilder value = new StringBuilder();
        while (!atEnd()) {
            char current = input.charAt(position++);
            if (current == '"') {
                return value.toString();
            }
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (atEnd()) {
                throw invalid("unterminated escape sequence");
            }
            char escaped = input.charAt(position++);
            if (escaped != '\\' && escaped != '"') {
                throw invalid("unsupported escape sequence");
            }
            value.append(escaped);
        }
        throw invalid("unterminated quoted value");
    }

    private boolean startsNullLiteral() {
        return input.regionMatches(true, position, "null", 0, 4)
                && (position + 4 == input.length() || isConditionBoundary(input.charAt(position + 4)));
    }

    private boolean consumeKeyword(String keyword) {
        int saved = position;
        skipWhitespace();
        if (!input.regionMatches(true, position, keyword, 0, keyword.length())) {
            position = saved;
            return false;
        }
        int after = position + keyword.length();
        if (after < input.length() && isIdentifierPart(input.charAt(after))) {
            position = saved;
            return false;
        }
        position = after;
        return true;
    }

    private boolean consume(String value) {
        if (!input.startsWith(value, position)) {
            return false;
        }
        position += value.length();
        return true;
    }

    private boolean consume(char value) {
        if (atEnd() || peek() != value) {
            return false;
        }
        position++;
        return true;
    }

    private void skipWhitespace() {
        while (!atEnd() && Character.isWhitespace(peek())) {
            position++;
        }
    }

    private boolean atEnd() {
        return position >= input.length();
    }

    private char peek() {
        return input.charAt(position);
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static boolean isConditionBoundary(char value) {
        return Character.isWhitespace(value) || value == ')';
    }

    private InvalidSearchRequestException invalid(String message) {
        return new InvalidSearchRequestException("invalid search: " + message);
    }

    sealed interface SearchNode permits ConditionNode, LogicalNode {
    }

    record ConditionNode(String field, SearchOperator operator, String value) implements SearchNode {
    }

    record LogicalNode(LogicalOperator operator, SearchNode left, SearchNode right) implements SearchNode {
    }

    enum LogicalOperator {
        AND, OR
    }

    enum SearchOperator {
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        IS_NULL,
        IS_NOT_NULL
    }
}
