package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.AggregateFunction;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.QueryAggregate;
import cn.superhuang.data.scalpel.dialect.query.QueryFilter;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterGroup;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QueryOrder;
import cn.superhuang.data.scalpel.dialect.query.QueryOrderTarget;
import cn.superhuang.data.scalpel.dialect.query.QueryProjection;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardQueryCompilerTest {

    private final DialectRegistry registry = BuiltInDialects.registry();

    @Test
    void compilesPostgresQueryWithOnlyControlledIdentifiersAndBoundValues() {
        var compiled = registry.require("POSTGRESQL").compileStandardQuery(query());

        assertEquals(
                "SELECT \"department\" AS \"department\", SUM(\"amount\") AS \"total_amount\" "
                        + "FROM \"sales\".\"order_fact\" WHERE (\"status\" = ? AND \"amount\" BETWEEN ? AND ?) "
                        + "GROUP BY \"department\" ORDER BY \"total_amount\" DESC LIMIT ? OFFSET ?",
                compiled.dataQuery().sql()
        );
        assertEquals("SELECT COUNT(*) FROM (SELECT \"department\" AS \"department\", SUM(\"amount\") AS \"total_amount\" "
                + "FROM \"sales\".\"order_fact\" WHERE (\"status\" = ? AND \"amount\" BETWEEN ? AND ?) "
                + "GROUP BY \"department\") ds_count", compiled.countQuery().sql());
        assertEquals(List.of("OPEN", new BigDecimal("10.00"), new BigDecimal("50.00"), 20, 40),
                compiled.dataQuery().parameters().stream().map(parameter -> parameter.value()).toList());
    }

    @Test
    void usesVendorSpecificPaginationForSqlServerAndDameng() {
        StandardQuery ungrouped = new StandardQuery(
                new TableIdentifier("sales", "dbo", "order_fact"),
                List.of(new QueryProjection("id", "id")),
                null, List.of(), List.of(), List.of(), 0, 10, false
        );

        assertEquals(
                "SELECT [id] AS [id] FROM [sales].[dbo].[order_fact] ORDER BY (SELECT 0) OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                registry.require("SQL_SERVER").compileStandardQuery(ungrouped).dataQuery().sql()
        );
        assertEquals(
                "SELECT * FROM (SELECT ds_inner.*, ROWNUM ds_row_number FROM (SELECT \"id\" AS \"id\" FROM \"dbo\".\"order_fact\") ds_inner "
                        + "WHERE ROWNUM <= ?) WHERE ds_row_number > ?",
                registry.require("DAMENG").compileStandardQuery(ungrouped).dataQuery().sql()
        );
    }

    private static StandardQuery query() {
        return new StandardQuery(
                new TableIdentifier("warehouse", "sales", "order_fact"),
                List.of(new QueryProjection("department", "department")),
                new QueryFilterGroup(ConditionConjunction.AND, List.of(
                        new QueryFilter("status", QueryValueType.STRING, QueryFilterOperator.EQ, List.of("OPEN")),
                        new QueryFilter("amount", QueryValueType.DECIMAL, QueryFilterOperator.BETWEEN,
                                List.of(new BigDecimal("10.00"), new BigDecimal("50.00")))
                )),
                List.of("department"),
                List.of(new QueryAggregate(AggregateFunction.SUM, "amount", "total_amount")),
                List.of(new QueryOrder("total_amount", QueryOrderTarget.AGGREGATE_ALIAS, QuerySortDirection.DESC)),
                40, 20, true
        );
    }
}
