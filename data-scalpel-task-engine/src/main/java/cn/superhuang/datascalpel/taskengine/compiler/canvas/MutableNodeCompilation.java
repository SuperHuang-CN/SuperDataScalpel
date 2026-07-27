package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeIssueSink;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CompilationIssue;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationState;

import java.util.ArrayList;
import java.util.List;

final class MutableNodeCompilation implements CanvasNodeIssueSink {
    private final String nodeId;
    private final List<CompilationIssue> issues = new ArrayList<>();
    private List<CanvasTableSchema> inputTables = List.of();
    private List<CanvasTableSchema> outputTables = List.of();

    MutableNodeCompilation(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void error(String code, String message, String path) {
        issues.add(CompilationIssue.node(code, message, nodeId, path));
    }

    @Override
    public void warning(String code, String message, String path) {
        issues.add(CompilationIssue.warning(code, message, nodeId, path));
    }

    @Override
    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == CompilationSeverity.ERROR);
    }

    void inputTables(List<CanvasTableSchema> tables) {
        this.inputTables = List.copyOf(tables);
    }

    void outputTables(List<CanvasTableSchema> tables) {
        this.outputTables = List.copyOf(tables);
    }

    NodeCompilationResult result() {
        NodeCompilationState state = hasErrors()
                ? NodeCompilationState.ERROR
                : issues.isEmpty() ? NodeCompilationState.OK : NodeCompilationState.WARNING;
        return new NodeCompilationResult(
                nodeId,
                state,
                inputTables,
                outputTables,
                List.copyOf(issues)
        );
    }
}
