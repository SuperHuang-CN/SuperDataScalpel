package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWritePredicateTest {
    @Test void bindsValuesAndQuotesMappedIdentifiers() {
        var dialect=BuiltInDialects.registry().require("POSTGRESQL");
        String value="x' OR 1=1 --";
        var prepared=JdbcWritePredicate.compile(dialect,new QueryFilter("a\"b",QueryValueType.STRING,QueryFilterOperator.EQ,List.of(value)),Set.of("a\"b"));
        assertEquals("\"a\"\"b\" = ?",prepared.sql());
        assertEquals(value,prepared.parameters().getFirst().value());
        assertFalse(prepared.sql().contains(value));
    }
    @Test void emptyGroupOrUnmappedColumnNeverWidensToFullDelete() {
        var dialect=BuiltInDialects.registry().require("POSTGRESQL");
        assertThrows(IllegalArgumentException.class,()->JdbcWritePredicate.compile(dialect,
                new QueryFilterGroup(ConditionConjunction.AND,List.of()),Set.of("id")));
        assertThrows(IllegalArgumentException.class,()->JdbcWritePredicate.compile(dialect,
                new QueryFilter("other",QueryValueType.INTEGER,QueryFilterOperator.EQ,List.of(1)),Set.of("id")));
        assertThrows(IllegalArgumentException.class,()->JdbcWritePredicate.compile(dialect,
                new QueryFilter("id",QueryValueType.INTEGER,QueryFilterOperator.IN,List.of()),Set.of("id")));
    }
}
