package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record NodeCompilationResult(
        String nodeId,
        NodeCompilationState state,
        List<CanvasTableSchema> inputTables,
        List<CanvasTableSchema> outputTables,
        List<CompilationIssue> issues
) {
}
