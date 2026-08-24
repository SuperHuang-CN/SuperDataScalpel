package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.JdbcInputReadOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcInputReadOptionPolicyTest {

    @Test
    void acceptsSupportedPerTableReadOptions() {
        RecordingIssues issues = validate(List.of(
                new JdbcInputReadOption("fetchsize", "10000"),
                new JdbcInputReadOption("pushDownAggregate", "true"),
                new JdbcInputReadOption("sessionInitStatement", "SET statement_timeout = '10min'")
        ));

        assertFalse(issues.hasErrors());
    }

    @Test
    void rejectsPlatformControlledSensitiveAndDuplicateOptions() {
        RecordingIssues issues = validate(List.of(
                new JdbcInputReadOption("dbtable", "orders"),
                new JdbcInputReadOption("apiKey", "secret"),
                new JdbcInputReadOption("fetchsize", "100"),
                new JdbcInputReadOption("FETCHSIZE", "200")
        ));

        assertTrue(issues.codes.contains("JDBC_READ_OPTION_RESERVED"));
        assertTrue(issues.codes.contains("JDBC_READ_OPTION_DUPLICATE"));
    }

    @Test
    void rejectsReservedPartitionAndInvalidTypedValues() {
        RecordingIssues issues = validate(List.of(
                new JdbcInputReadOption("partitionColumn", "id"),
                new JdbcInputReadOption("queryTimeout", "-1"),
                new JdbcInputReadOption("pushDownLimit", "yes"),
                new JdbcInputReadOption("sessionInitStatement", "  ")
        ));

        assertTrue(issues.codes.contains("JDBC_READ_OPTION_RESERVED"));
        assertTrue(issues.codes.contains("JDBC_READ_OPTION_VALUE_INVALID"));
    }

    private static RecordingIssues validate(List<JdbcInputReadOption> options) {
        RecordingIssues issues = new RecordingIssues();
        JdbcInputReadOptionPolicy.validate(options, "configuration.tables[0].readOptions", issues);
        return issues;
    }

    private static final class RecordingIssues implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public void warning(String code, String message, String path) {
        }

        @Override
        public boolean hasErrors() {
            return !codes.isEmpty();
        }
    }
}
