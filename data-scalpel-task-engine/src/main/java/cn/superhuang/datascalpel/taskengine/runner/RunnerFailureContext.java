package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;

record RunnerFailureContext(
        String nodeId,
        String nodeType,
        String nodeName,
        ExecutionFailurePhase phase,
        String resourceName
) {
    static RunnerFailureContext task(ExecutionFailurePhase phase) {
        return new RunnerFailureContext(null, null, null, phase, null);
    }

    static RunnerFailureContext node(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            String resourceName
    ) {
        return new RunnerFailureContext(
                node.id(), node.nodeType().name(), node.name(), phase, resourceName);
    }
}
