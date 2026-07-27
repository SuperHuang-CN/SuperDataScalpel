package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import org.apache.spark.SparkException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunnerFailureClassifierTest {
    private final RunnerFailureClassifier classifier = new RunnerFailureClassifier();
    private final RunnerFailureContext input = new RunnerFailureContext(
            "65b9615d-b72a-42c1-8e4e-f28a660da082", "JDBC_INPUT", "用户输入",
            ExecutionFailurePhase.READ, "dev_source.sys_user");

    @Test
    void findsPermissionSqlStateInsideSparkWrapper() {
        TaskExecutionError error = classifier.classify(
                new SparkException("Spark JDBC failed", new SQLException("permission denied", "42501")), input);

        assertEquals("JDBC_PERMISSION_DENIED", error.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, error.category());
        assertEquals("42501", error.sqlState());
        assertEquals("数据源用户无权读取表 dev_source.sys_user", error.message());
        assertFalse(error.retryable());
        assertNotNull(error.diagnosticId());
    }

    @Test
    void mapsStableJdbcCategoriesAndRetryability() {
        assertClassified(new SQLException("auth", "28000"), "JDBC_AUTHENTICATION_FAILED",
                ExecutionErrorCategory.AUTHENTICATION, false);
        assertClassified(new SQLException("connect", "08006"), "JDBC_CONNECTION_FAILED",
                ExecutionErrorCategory.CONNECTION, true);
        assertClassified(new SQLException("constraint", "23505"), "JDBC_CONSTRAINT_VIOLATION",
                ExecutionErrorCategory.CONSTRAINT, false);
        assertClassified(new SQLTimeoutException("timeout"), "JDBC_TIMEOUT",
                ExecutionErrorCategory.TIMEOUT, true);
        assertClassified(new RuntimeException(new SocketTimeoutException("read timeout")), "JDBC_TIMEOUT",
                ExecutionErrorCategory.TIMEOUT, true);
    }

    @Test
    void preservesRuntimeSchemaMismatchAndFallsBackByNodeType() {
        TaskExecutionError schema = classifier.classify(
                new RunnerExecutionException("RUNTIME_SCHEMA_MISMATCH", "changed", input.nodeId()), input);
        assertEquals("RUNTIME_SCHEMA_MISMATCH", schema.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, schema.category());
        assertNull(schema.sqlState());

        RunnerFailureContext join = new RunnerFailureContext(
                "e55c50d7-374d-4fe0-aede-e1648988af23", "JOIN", "订单客户 Join",
                ExecutionFailurePhase.PROCESS, "order_customer");
        TaskExecutionError processor = classifier.classify(new IllegalStateException("boom"), join);
        assertEquals("PROCESSOR_EXECUTION_FAILED", processor.code());
        assertEquals(ExecutionErrorCategory.INTERNAL, processor.category());

        RunnerFailureContext rename = new RunnerFailureContext(
                "8c9173e0-47e7-4870-8a37-9e41b9e39be7", "RENAME", "订单重命名",
                ExecutionFailurePhase.PROCESS, "source_orders");
        TaskExecutionError renameProcessor = classifier.classify(new IllegalStateException("boom"), rename);
        assertEquals("PROCESSOR_EXECUTION_FAILED", renameProcessor.code());
        assertEquals(ExecutionErrorCategory.INTERNAL, renameProcessor.category());
    }

    @Test
    void fallsBackToModelNodeErrorsWithoutHidingJdbcSqlState() {
        RunnerFailureContext modelInput = new RunnerFailureContext(
                "9d7ae286-591d-4eca-8eff-c002a37ce5d2", "MODEL_INPUT", "订单模型输入",
                ExecutionFailurePhase.READ, "model_schema.orders");
        RunnerFailureContext modelOutput = new RunnerFailureContext(
                "b797597b-eb3e-428c-aaf5-186fa8d7fc28", "MODEL_OUTPUT", "订单模型输出",
                ExecutionFailurePhase.WRITE, "model_schema.dwd_orders");

        TaskExecutionError inputFallback = classifier.classify(new IllegalStateException("boom"), modelInput);
        TaskExecutionError outputFallback = classifier.classify(new IllegalStateException("boom"), modelOutput);
        TaskExecutionError permission = classifier.classify(
                new SQLException("permission denied", "42501"), modelOutput);

        assertEquals("MODEL_INPUT_FAILED", inputFallback.code());
        assertEquals("模型输入节点执行失败", inputFallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, inputFallback.category());
        assertEquals("MODEL_OUTPUT_FAILED", outputFallback.code());
        assertEquals("模型输出节点执行失败", outputFallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, outputFallback.category());
        assertEquals("JDBC_PERMISSION_DENIED", permission.code());
        assertEquals("42501", permission.sqlState());
    }

    @Test
    void classifiesHttpApiFailuresWithReadPhaseAndSafeStableCodes() {
        RunnerFailureContext httpInput = new RunnerFailureContext(
                "c99104f5-05cb-413a-999c-dcf35cce51b8", "HTTP_API_INPUT", "订单 API 输入",
                ExecutionFailurePhase.READ, "8cf73463-d1b5-4327-bce3-f8e94b965be1");

        TaskExecutionError authentication = classifier.classify(
                new RunnerExecutionException(
                        "API_AUTHENTICATION_FAILED", "token=should-not-be-returned", httpInput.nodeId()),
                httpInput);
        TaskExecutionError fallback = classifier.classify(new IllegalStateException("remote failure"), httpInput);
        TaskExecutionError staging = classifier.classify(
                new RunnerExecutionException(
                        "API_BATCH_STAGING_FAILED", "spark-local-dir=/sensitive/path", httpInput.nodeId()),
                httpInput);

        assertEquals("API_AUTHENTICATION_FAILED", authentication.code());
        assertEquals("HTTP API 认证失败", authentication.message());
        assertEquals(ExecutionErrorCategory.AUTHENTICATION, authentication.category());
        assertEquals(ExecutionFailurePhase.READ, authentication.phase());
        assertFalse(authentication.message().contains("should-not-be-returned"));
        assertNotNull(authentication.diagnosticId());
        assertEquals("HTTP_API_INPUT_FAILED", fallback.code());
        assertEquals("HTTP API 输入节点执行失败", fallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, fallback.category());
        assertEquals("API_BATCH_STAGING_FAILED", staging.code());
        assertEquals("HTTP API 分批暂存失败", staging.message());
        assertEquals(ExecutionErrorCategory.RESOURCE, staging.category());
        assertFalse(staging.retryable());
        assertFalse(staging.message().contains("sensitive"));
    }

    @Test
    void preservesFileInputIdentityWhenLazyReadFailsDuringOutputAction() {
        String fileNodeId = "80efb303-fd40-47f3-9a18-a41e746d7be3";
        RunnerFailureContext output = new RunnerFailureContext(
                "cc55b3c8-dd26-4f19-be66-61cd5b49b8cd",
                "JDBC_OUTPUT",
                "订单输出",
                ExecutionFailurePhase.WRITE,
                "orders_target"
        );
        FileDatasetReadException readFailure = new FileDatasetReadException(
                "FILE_DATASET_PARSE_FAILED",
                "文件数据集内容解析失败",
                fileNodeId,
                "订单文件输入",
                false,
                new IllegalArgumentException("invalid parquet")
        );

        TaskExecutionError error = classifier.classify(new SparkException("write failed", readFailure), output);

        assertEquals("FILE_DATASET_PARSE_FAILED", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertEquals(ExecutionFailurePhase.READ, error.phase());
        assertEquals(fileNodeId, error.nodeId());
        assertEquals("FILE_DATASET_INPUT", error.nodeType());
        assertEquals("订单文件输入", error.nodeName());
        assertFalse(error.retryable());
    }

    private void assertClassified(
            Throwable throwable,
            String code,
            ExecutionErrorCategory category,
            boolean retryable
    ) {
        TaskExecutionError error = classifier.classify(throwable, input);
        assertEquals(code, error.code());
        assertEquals(category, error.category());
        assertEquals(retryable, error.retryable());
    }
}
