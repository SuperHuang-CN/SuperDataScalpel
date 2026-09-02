package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.QueryFilter;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterGroup;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryField;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterGroupInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryLimits;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryOrderInput;
import cn.superhuang.data.scalpel.dialect.query.StandardTableQueryCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardTableQueryCompilerTest {

    private final StandardTableQueryCompiler compiler = new StandardTableQueryCompiler();
    private final StandardQueryLimits limits = new StandardQueryLimits(50, 100, 20, 5, 1_000, 3, 0, 0, 10_000);

    @Test
    void resolvesOnlyWhitelistedFieldsAndAppendsPrimaryKeyForStablePaging() {
        var result = compiler.compile(
                new TableIdentifier(null, "public", "orders"),
                List.of(
                        new StandardQueryField("id", "id", QueryValueType.LONG, true, true),
                        new StandardQueryField("amount", "amount", QueryValueType.DECIMAL, false, true),
                        new StandardQueryField("payload", "payload", null, false, false)
                ),
                new StandardQueryInput(
                        2, 20, List.of("id", "amount"),
                        new StandardQueryFilterGroupInput(ConditionConjunction.AND, List.of(
                                new StandardQueryFilterInput("amount", QueryFilterOperator.GE, "12.50")
                        )),
                        List.of(new StandardQueryOrderInput("amount", QuerySortDirection.DESC)),
                        List.of(), List.of(), false
                ),
                limits
        );

        assertEquals(2, result.pageNo());
        assertEquals(20, result.pageSize());
        assertEquals(20, result.query().offset());
        assertEquals(List.of("id", "amount"), result.query().projections().stream().map(projection -> projection.alias()).toList());
        assertEquals(List.of("amount", "id"), result.query().orders().stream().map(order -> order.target()).toList());
        QueryFilterGroup root = (QueryFilterGroup) result.query().filter();
        assertEquals("12.50", ((QueryFilter) root.conditions().getFirst()).values().getFirst().toString());
    }

    @Test
    void rejectsBinaryFieldsAndExcessiveOffsets() {
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new TableIdentifier(null, "public", "orders"),
                List.of(new StandardQueryField("payload", "payload", null, false, false)),
                new StandardQueryInput(1, 20, List.of("payload"), null, List.of(), List.of(), List.of(), false),
                limits
        ));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new TableIdentifier(null, "public", "orders"),
                List.of(new StandardQueryField("id", "id", QueryValueType.LONG, true, true)),
                new StandardQueryInput(502, 20, List.of(), null, List.of(), List.of(), List.of(), false),
                limits
        ));
    }
}
