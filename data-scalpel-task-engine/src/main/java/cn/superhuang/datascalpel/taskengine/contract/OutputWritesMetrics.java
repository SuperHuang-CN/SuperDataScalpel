package cn.superhuang.datascalpel.taskengine.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record OutputWritesMetrics(List<OutputWriteExecutionResult> writes)
        implements NodeExecutionMetrics {

    public OutputWritesMetrics {
        writes = writes == null ? List.of() : List.copyOf(writes);
        if (writes.isEmpty()) throw new IllegalArgumentException("输出写入指标不能为空");
        Set<java.util.UUID> writeIds = new HashSet<>();
        for (OutputWriteExecutionResult write : writes) {
            if (write == null || !writeIds.add(java.util.UUID.fromString(write.writeId()))) {
                throw new IllegalArgumentException("输出写入指标包含空项或重复 writeId");
            }
        }
    }

    public Long rowsWritten() {
        Long total = 0L;
        for (OutputWriteExecutionResult write : writes) {
            if (write.state() != OutputWriteExecutionState.SUCCESS) continue;
            if (write.affectedRows() == null || total == null) return null;
            try {
                total = Math.addExact(total, write.affectedRows());
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return total;
    }

    public boolean allSucceeded() {
        return writes.stream().allMatch(write -> write.state() == OutputWriteExecutionState.SUCCESS);
    }

    public OutputWriteExecutionResult failedWrite() {
        return writes.stream()
                .filter(write -> write.state() == OutputWriteExecutionState.FAILED)
                .findFirst().orElse(null);
    }

    public boolean terminalSequenceValid() {
        boolean failed = false;
        for (OutputWriteExecutionResult write : writes) {
            if (write.state() == OutputWriteExecutionState.PENDING
                    || write.state() == OutputWriteExecutionState.RUNNING) return false;
            if (!failed && write.state() == OutputWriteExecutionState.SUCCESS) continue;
            if (!failed && write.state() == OutputWriteExecutionState.FAILED) {
                failed = true;
                continue;
            }
            if (failed && write.state() == OutputWriteExecutionState.SKIPPED) continue;
            return false;
        }
        return true;
    }
}
