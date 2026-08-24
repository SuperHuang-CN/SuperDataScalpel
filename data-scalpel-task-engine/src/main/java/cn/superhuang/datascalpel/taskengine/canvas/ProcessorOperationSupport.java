package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ProcessorOperation;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared routing, validation and reconstruction for simple multi-table processors. */
final class ProcessorOperationSupport {

    static final String INTERNAL_OPERATION_ID = "__datascalpel_internal_single_operation";

    private ProcessorOperationSupport() {
    }

    static boolean isInternalSingle(List<? extends ProcessorOperation> operations) {
        return operations != null && operations.size() == 1
                && operations.getFirst() != null
                && INTERNAL_OPERATION_ID.equals(operations.getFirst().operationId());
    }

    static CanvasNodeOperationResult apply(
            List<? extends ProcessorOperation> operations,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context,
            boolean allowReplaceRename,
            SingleOperationExecutor executor
    ) {
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        CanvasNodeIssueSink issues = context.issues();
        if (operations == null) {
            issues.error("REQUIRED_CONFIGURATION", "处理操作必须是数组", "configuration.operations");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (operations.isEmpty()) {
            issues.error("EMPTY_PROCESSOR_OPERATIONS", "至少配置一张处理表", "configuration.operations");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        List<ResolvedOperation> resolved = new ArrayList<>(operations.size());
        Set<String> operationIds = new HashSet<>();
        Set<String> sourceNames = new HashSet<>();
        Map<String, Integer> generatedNames = new HashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            ProcessorOperation operation = operations.get(index);
            String path = "configuration.operations[" + index + "]";
            if (operation == null) {
                issues.error("REQUIRED_CONFIGURATION", "处理操作不能为空", path);
                continue;
            }
            validateOperationId(operation.operationId(), path + ".operationId", operationIds, issues);
            CanvasNodeSupport.required(operation.sourceTableName(), "请选择来源表", path + ".sourceTableName", issues);
            if (!CanvasNodeSupport.blank(operation.sourceTableName())
                    && !sourceNames.add(operation.sourceTableName())) {
                issues.error(
                        "DUPLICATE_PROCESSOR_SOURCE_TABLE",
                        "同一来源表只能配置一次：" + operation.sourceTableName(),
                        path + ".sourceTableName"
                );
            }
            SparkCanvasTable source = CanvasNodeSupport.blank(operation.sourceTableName())
                    ? null : inputs.get(operation.sourceTableName());
            if (!CanvasNodeSupport.blank(operation.sourceTableName()) && source == null) {
                issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + operation.sourceTableName(),
                        path + ".sourceTableName");
            }
            ResolvedOutput output = validateOutput(
                    operation, source, inputs, allowReplaceRename, generatedNames, path, issues
            );
            if (source != null && output != null) {
                resolved.add(new ResolvedOperation(index, operation, source, output.tableName(), output.replacesSource()));
            }
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<Integer, SparkCanvasTable> transformed = new LinkedHashMap<>();
        for (ResolvedOperation operation : resolved) {
            BufferedIssueSink bufferedIssues = new BufferedIssueSink();
            CanvasNodeOperationContext scopedContext = new CanvasNodeOperationContext(
                    context.sparkSession(), context.metadataIndex(),
                    new PathPrefixIssueSink(bufferedIssues, "configuration.operations[" + operation.index() + "]"),
                    context.dataAccess(), context.executionMode(), context.runtimeValues()
            );
            CanvasNodeOperationResult result = executor.execute(operation, scopedContext);
            bufferedIssues.replayTo(issues);
            if (bufferedIssues.hasErrors()) {
                continue;
            }
            SparkCanvasTable table = result.propagatedTables().get(operation.outputTableName());
            if (table == null) {
                issues.error("REQUIRED_CONFIGURATION", "无法生成处理结果表", "configuration.operations[" + operation.index() + "]");
                continue;
            }
            transformed.put(operation.index(), table);
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, ResolvedOperation> replacing = new HashMap<>();
        for (ResolvedOperation operation : resolved) {
            if (operation.replacesSource()) {
                replacing.put(operation.operation().sourceTableName(), operation);
            }
        }
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        for (Map.Entry<String, SparkCanvasTable> entry : inputs.entrySet()) {
            ResolvedOperation replacement = replacing.get(entry.getKey());
            if (replacement == null) {
                output.put(entry.getKey(), entry.getValue());
            } else {
                output.put(replacement.outputTableName(), transformed.get(replacement.index()));
            }
        }
        for (ResolvedOperation operation : resolved) {
            if (!operation.replacesSource()) {
                output.put(operation.outputTableName(), transformed.get(operation.index()));
            }
        }
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateOperationId(
            String operationId, String path, Set<String> operationIds, CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(operationId)) {
            issues.error("INVALID_PROCESSOR_OPERATION_ID", "处理操作 ID 必须是 UUID", path);
            return;
        }
        try {
            UUID.fromString(operationId);
        } catch (IllegalArgumentException exception) {
            issues.error("INVALID_PROCESSOR_OPERATION_ID", "处理操作 ID 必须是 UUID", path);
            return;
        }
        if (!operationIds.add(operationId)) {
            issues.error("DUPLICATE_PROCESSOR_OPERATION_ID", "处理操作 ID 重复", path);
        }
    }

    private static ResolvedOutput validateOutput(
            ProcessorOperation operation,
            SparkCanvasTable source,
            Map<String, SparkCanvasTable> inputs,
            boolean allowReplaceRename,
            Map<String, Integer> generatedNames,
            String path,
            CanvasNodeIssueSink issues
    ) {
        ProcessorOutput output = operation.output();
        if (output == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择输出方式", path + ".output");
            return null;
        }
        if (output instanceof ProcessorOutput.ReplaceSource replace) {
            String renamedTableName = replace.outputTableName();
            if (renamedTableName != null && CanvasNodeSupport.blank(renamedTableName)) {
                issues.error("REQUIRED_CONFIGURATION", "输出表名不能为空", path + ".output.outputTableName");
                return null;
            }
            if (!allowReplaceRename && renamedTableName != null) {
                issues.error(
                        "PROCESSOR_REPLACE_OUTPUT_NAME_NOT_ALLOWED",
                        "覆盖来源表时不能修改逻辑表名",
                        path + ".output.outputTableName"
                );
                return null;
            }
            String target = renamedTableName == null ? operation.sourceTableName() : renamedTableName;
            if (source != null && !target.equals(operation.sourceTableName()) && inputs.containsKey(target)) {
                issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + target,
                        path + ".output.outputTableName");
                return null;
            }
            if (renamedTableName != null && !target.equals(operation.sourceTableName())) {
                validateGeneratedName(target, generatedNames, path, issues);
            }
            return new ResolvedOutput(target, true);
        }
        if (output instanceof ProcessorOutput.CreateNewTable create) {
            String tableName = create.outputTableName();
            CanvasNodeSupport.required(tableName, "请输入输出表名", path + ".output.outputTableName", issues);
            if (CanvasNodeSupport.blank(tableName)) {
                return null;
            }
            if (inputs.containsKey(tableName)) {
                issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + tableName,
                        path + ".output.outputTableName");
                return null;
            }
            validateGeneratedName(tableName, generatedNames, path, issues);
            return new ResolvedOutput(tableName, false);
        }
        issues.error("REQUIRED_CONFIGURATION", "输出方式无效", path + ".output");
        return null;
    }

    private static void validateGeneratedName(
            String tableName, Map<String, Integer> generatedNames, String path, CanvasNodeIssueSink issues
    ) {
        Integer previous = generatedNames.putIfAbsent(tableName, 1);
        if (previous != null) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名重复：" + tableName,
                    path + ".output.outputTableName");
        }
    }

    record ResolvedOperation(
            int index,
            ProcessorOperation operation,
            SparkCanvasTable source,
            String outputTableName,
            boolean replacesSource
    ) {
        String temporarySourceTableName() {
            return "__datascalpel_processor_operation_" + index;
        }
    }

    @FunctionalInterface
    interface SingleOperationExecutor {
        CanvasNodeOperationResult execute(ResolvedOperation operation, CanvasNodeOperationContext context);
    }

    private record ResolvedOutput(String tableName, boolean replacesSource) {
    }

    private record Issue(boolean warning, String code, String message, String path) {
    }

    private static final class BufferedIssueSink implements CanvasNodeIssueSink {
        private final List<Issue> issues = new ArrayList<>();
        @Override public void error(String code, String message, String path) { issues.add(new Issue(false, code, message, path)); }
        @Override public void warning(String code, String message, String path) { issues.add(new Issue(true, code, message, path)); }
        @Override public boolean hasErrors() { return issues.stream().anyMatch(issue -> !issue.warning()); }
        void replayTo(CanvasNodeIssueSink target) {
            for (Issue issue : issues) {
                if (issue.warning()) target.warning(issue.code(), issue.message(), issue.path());
                else target.error(issue.code(), issue.message(), issue.path());
            }
        }
    }

    private record PathPrefixIssueSink(CanvasNodeIssueSink delegate, String prefix) implements CanvasNodeIssueSink {
        @Override public void error(String code, String message, String path) { delegate.error(code, message, path(path)); }
        @Override public void warning(String code, String message, String path) { delegate.warning(code, message, path(path)); }
        @Override public boolean hasErrors() { return delegate.hasErrors(); }
        private String path(String path) {
            return path != null && path.startsWith("configuration")
                    ? prefix + path.substring("configuration".length()) : prefix;
        }
    }
}
