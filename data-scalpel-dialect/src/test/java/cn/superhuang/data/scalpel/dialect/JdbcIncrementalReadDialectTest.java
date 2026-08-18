package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JdbcIncrementalReadDialectTest {

    @Test
    void rendersOneCompleteBoundedWindowWithoutLimitOrderOrPagination() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("POSTGRESQL");
        String sql = ((JdbcIncrementalReadDialect) dialect).renderIncrementalWindowQuery(
                dialect,
                new TableIdentifier("orders", "public", "order_event"),
                List.of("id", "changed_at"),
                "changed_at",
                true
        );

        assertEquals("SELECT \"id\", \"changed_at\" FROM \"public\".\"order_event\" "
                + "WHERE \"changed_at\" > ? AND \"changed_at\" <= ?", sql);
        assertFalse(sql.toUpperCase(java.util.Locale.ROOT).contains("LIMIT"));
        assertFalse(sql.toUpperCase(java.util.Locale.ROOT).contains("ORDER BY"));
        assertFalse(sql.toUpperCase(java.util.Locale.ROOT).contains("OFFSET"));
    }

    @Test
    void rendersEarliestWindowWithOnlyUpperBound() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("MYSQL");
        String sql = ((JdbcIncrementalReadDialect) dialect).renderIncrementalWindowQuery(
                dialect,
                new TableIdentifier("sales", null, "order_event"),
                List.of("id", "changed_at"),
                "changed_at",
                false
        );

        assertEquals("SELECT `id`, `changed_at` FROM `sales`.`order_event` WHERE `changed_at` <= ?", sql);
    }
}
