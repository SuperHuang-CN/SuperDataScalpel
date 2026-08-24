package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import org.apache.spark.sql.SparkSession;

import java.util.Objects;

public record CanvasNodeOperationContext(
        SparkSession sparkSession,
        MetadataIndex metadataIndex,
        CanvasNodeIssueSink issues,
        CanvasNodeDataAccess dataAccess,
        CanvasExecutionMode executionMode,
        CanvasRuntimeValues runtimeValues
) {
    public CanvasNodeOperationContext {
        Objects.requireNonNull(sparkSession, "sparkSession");
        Objects.requireNonNull(metadataIndex, "metadataIndex");
        Objects.requireNonNull(issues, "issues");
        Objects.requireNonNull(dataAccess, "dataAccess");
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(runtimeValues, "runtimeValues");
    }

    public CanvasNodeOperationContext(
            SparkSession sparkSession,
            MetadataIndex metadataIndex,
            CanvasNodeIssueSink issues,
            CanvasNodeDataAccess dataAccess
    ) {
        this(
                sparkSession,
                metadataIndex,
                issues,
                dataAccess,
                CanvasExecutionMode.BATCH,
                CanvasRuntimeValues.forPreview()
        );
    }
}
