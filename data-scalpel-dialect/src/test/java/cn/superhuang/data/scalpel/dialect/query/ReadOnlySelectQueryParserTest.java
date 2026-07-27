package cn.superhuang.data.scalpel.dialect.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlySelectQueryParserTest {

    @Test
    void acceptsASingleSelectAndStripsItsTerminalSemicolon() {
        InsertSelectQuery query = ReadOnlySelectQueryParser.parse("SELECT 'update' AS action -- delete is only text\n;");

        assertNull(query.withClause());
        assertEquals("SELECT 'update' AS action -- delete is only text", query.selectSql());
    }

    @Test
    void separatesCteFromItsFinalTopLevelSelect() {
        InsertSelectQuery query = ReadOnlySelectQueryParser.parse("""
                /* prepare input */
                WITH recent AS (
                    SELECT customer_id, amount FROM source_order
                ), totals AS (
                    SELECT customer_id, sum(amount) AS amount FROM recent GROUP BY customer_id
                )
                SELECT customer_id, amount FROM totals
                """);

        assertEquals("/* prepare input */\nWITH recent AS (\n    SELECT customer_id, amount FROM source_order\n), totals AS (\n    SELECT customer_id, sum(amount) AS amount FROM recent GROUP BY customer_id\n)", query.withClause());
        assertEquals("SELECT customer_id, amount FROM totals", query.selectSql());
    }

    @Test
    void rejectsStatementsAndWriteKeywordsOutsideQuotedValues() {
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySelectQueryParser.parse("SELECT * FROM source; DELETE FROM target"));
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySelectQueryParser.parse("WITH changed AS (UPDATE source SET name = 'x') SELECT * FROM changed"));
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySelectQueryParser.parse("SELECT * INTO target FROM source"));
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySelectQueryParser.parse("UPDATE source SET name = 'x'"));
    }

    @Test
    void ignoresKeywordsAndSemicolonsInQuotedOrCommentedContent() {
        InsertSelectQuery query = ReadOnlySelectQueryParser.parse("""
                WITH nested AS (
                  SELECT 'insert; update' AS text_value /* delete; merge */
                )
                SELECT text_value AS result FROM nested -- drop table
                """);

        assertEquals("result", query.selectSql().substring(query.selectSql().indexOf("AS ") + 3, query.selectSql().indexOf(" FROM")));
    }

    @Test
    void serviceQueriesRejectRuntimeControlledPaginationAndLocking() {
        assertThrows(IllegalArgumentException.class,
                () -> ReadOnlySelectQueryParser.parseServiceQuery("SELECT * FROM customer LIMIT 20"));
        assertThrows(IllegalArgumentException.class,
                () -> ReadOnlySelectQueryParser.parseServiceQuery("SELECT * FROM customer OFFSET 10"));
        assertThrows(IllegalArgumentException.class,
                () -> ReadOnlySelectQueryParser.parseServiceQuery("SELECT * FROM customer FOR UPDATE"));
        assertThrows(IllegalArgumentException.class,
                () -> ReadOnlySelectQueryParser.parseServiceQuery("SELECT * FROM customer FOR SHARE"));

        ReadOnlySelectQueryParser.parseServiceQuery("""
                SELECT * FROM (SELECT * FROM customer LIMIT 20) nested
                ORDER BY nested.id
                """);
    }
}
