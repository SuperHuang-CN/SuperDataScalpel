package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import org.apache.spark.sql.SparkSession;

import java.util.Objects;

public record CanvasNodeOperationContext(
        SparkSession sparkSession,
        MetadataIndex metadataIndex,
        CanvasNodeIssueSink issues,
        CanvasNodeDataAccess dataAccess
) {
    public CanvasNodeOperationContext {
        Objects.requireNonNull(sparkSession, "sparkSession");
        Objects.requireNonNull(metadataIndex, "metadataIndex");
        Objects.requireNonNull(issues, "issues");
        Objects.requireNonNull(dataAccess, "dataAccess");
    }
}
