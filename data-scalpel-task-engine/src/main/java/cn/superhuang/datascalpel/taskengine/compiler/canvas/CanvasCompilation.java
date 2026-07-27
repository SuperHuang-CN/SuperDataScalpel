package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CompilationIssue;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;

import java.util.List;

public record CanvasCompilation(
        boolean valid,
        List<CompilationIssue> canvasIssues,
        List<NodeCompilationResult> nodeResults
) {
    static CanvasCompilation of(List<CompilationIssue> canvasIssues, List<NodeCompilationResult> nodeResults) {
        boolean valid = canvasIssues.stream().noneMatch(issue -> issue.severity() == CompilationSeverity.ERROR)
                && nodeResults.stream().flatMap(result -> result.issues().stream())
                .noneMatch(issue -> issue.severity() == CompilationSeverity.ERROR);
        return new CanvasCompilation(valid, List.copyOf(canvasIssues), List.copyOf(nodeResults));
    }
}
