package cn.superhuang.data.scalpel.dialect.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedParameterSqlCompilerTest {

    @Test
    void compilesRepeatedNamedParametersInOccurrenceOrder() {
        SqlTemplateCompilation result = NamedParameterSqlCompiler.compile("""
                SELECT id FROM customer
                WHERE department_id = :departmentId
                  AND (:keyword IS NULL OR name ILIKE :keyword)
                """);

        assertEquals(List.of("departmentId", "keyword", "keyword"), result.bindingOrder());
        assertEquals("""
                SELECT id FROM customer
                WHERE department_id = ?
                  AND (? IS NULL OR name ILIKE ?)
                """.trim(), result.jdbcSql());
    }

    @Test
    void ignoresQuotedCommentedAndPostgresCastColons() {
        SqlTemplateCompilation result = NamedParameterSqlCompiler.compile("""
                SELECT ':ignored', ":quoted", $$:dollar$$, value::text
                FROM customer -- :commented
                WHERE id = :id /* :alsoIgnored */
                """);

        assertEquals(List.of("id"), result.bindingOrder());
        assertEquals(1, NamedParameterSqlCompiler.countJdbcPlaceholders(result.jdbcSql()));
        assertEquals(1, NamedParameterSqlCompiler.countJdbcPlaceholders("SELECT '?' AS literal, ? -- ?\n/* ? */"));
    }

    @Test
    void rejectsTextSubstitutionAndUnclosedContent() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedParameterSqlCompiler.compile("SELECT ${column} FROM customer"));
        assertThrows(IllegalArgumentException.class,
                () -> NamedParameterSqlCompiler.compile("SELECT ':unclosed"));
    }
}
