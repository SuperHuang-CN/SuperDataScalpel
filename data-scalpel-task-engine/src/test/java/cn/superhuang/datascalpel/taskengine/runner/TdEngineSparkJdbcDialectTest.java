package cn.superhuang.datascalpel.taskengine.runner;

import org.apache.spark.sql.jdbc.JdbcDialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TdEngineSparkJdbcDialectTest {
    @Test
    void bothTransportsUseColumnIdentifiersInsteadOfStringLiterals() {
        TdEngineSparkJdbcDialect.ensureRegistered();
        for (String url : new String[]{"jdbc:TAOS-WS://localhost:6041/qa", "jdbc:TAOS-RS://localhost:6041/qa"}) {
            assertEquals("`reading`", JdbcDialects.get(url).quoteIdentifier("reading"));
            assertEquals("`测点``名称`", JdbcDialects.get(url).quoteIdentifier("测点`名称"));
        }
        assertFalse(new TdEngineSparkJdbcDialect().canHandle("jdbc:postgresql://localhost/qa"));
    }
}
