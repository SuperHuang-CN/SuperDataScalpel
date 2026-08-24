package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.task.service.CanvasDefinitionValidator;
import cn.superhuang.data.scalpel.contract.task.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministically turns the restricted semantic plan into the current Canvas contract. */
@Component
public class TaskCanvasDefinitionBuilder {

    private static final double START_X = 80;
    private static final double START_Y = 90;
    private static final double X_GAP = 300;
    private static final double Y_GAP = 210;
    private static final double WIDTH = 220;
    private static final double HEIGHT = 112;
    private static final int MAX_NODES = 20;

    private final CanvasDefinitionValidator validator;

    public TaskCanvasDefinitionBuilder(CanvasDefinitionValidator validator) {
        this.validator = validator;
    }

    public CanvasDefinition build(
            TaskCanvasPlan plan,
            Map<String, AssistantTaskCanvasQueryService.ResourceFingerprint> inputResources,
            Map<String, AssistantTaskCanvasQueryService.SafeSchema> inputSchemas,
            AssistantTaskCanvasQueryService.SafeSchema outputSchema
    ) {
        validatePlan(plan, inputResources);
        List<CanvasNodeDefinition> nodes = new ArrayList<>();
        List<CanvasEdgeDefinition> edges = new ArrayList<>();
        Map<String, String> nodeByRef = new LinkedHashMap<>();
        Map<String, String> tableByRef = new LinkedHashMap<>();
        Map<String, List<String>> fieldsByRef = new LinkedHashMap<>();
        Map<String, Integer> depthByRef = new HashMap<>();
        Map<String, Double> yByRef = new HashMap<>();
        Map<String, Integer> tableCounts = new HashMap<>();

        for (int index = 0; index < plan.inputs().size(); index++) {
            TaskCanvasPlan.Input input = plan.inputs().get(index);
            AssistantTaskCanvasQueryService.ResourceFingerprint resource = inputResources.get(input.ref());
            String nodeId = uuid();
            double y = START_Y + index * Y_GAP;
            String logicalTable = input.type() == TaskCanvasPlan.InputType.JDBC_TABLE
                    ? input.tableName() : resource.code();
            if (logicalTable == null || logicalTable.isBlank()) logicalTable = "input_" + (index + 1);
            CanvasNodeDefinition inputNode = switch (input.type()) {
                case MODEL -> new ModelInputNodeDefinition(
                        nodeId, input.name(), layout(0, y),
                        new ModelInputConfiguration(List.of(new ModelInputSelection(input.modelId().toString())))
                );
                case JDBC_TABLE -> new JdbcInputNodeDefinition(
                        nodeId, input.name(), layout(0, y),
                        new JdbcInputConfiguration(
                                input.dataSourceId().toString(),
                                List.of(new JdbcInputTableSelection(input.tableName())))
                );
                case FILE_DATASET_TABLE -> new FileDatasetInputNodeDefinition(
                        nodeId, input.name(), layout(0, y),
                        new FileDatasetInputConfiguration(
                                input.fileDatasetId().toString(),
                                List.of(new FileDatasetInputTableSelection(input.fileDatasetTableId().toString()))
                        )
                );
            };
            nodes.add(inputNode);
            nodeByRef.put(input.ref(), nodeId);
            tableByRef.put(input.ref(), logicalTable);
            AssistantTaskCanvasQueryService.SafeSchema inputSchema = inputSchemas.get(input.ref());
            if (inputSchema == null) throw badRequest("输入资源 Schema 未解析：" + input.ref());
            fieldsByRef.put(input.ref(), inputSchema.fields().stream()
                    .map(AssistantTaskCanvasQueryService.SafeField::code)
                    .toList());
            depthByRef.put(input.ref(), 0);
            yByRef.put(input.ref(), y);
            tableCounts.merge(logicalTable.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        // Two sources with the same logical name must be separated before their branches converge.
        for (TaskCanvasPlan.Input input : plan.inputs()) {
            String logicalTable = tableByRef.get(input.ref());
            if (tableCounts.getOrDefault(logicalTable.toLowerCase(Locale.ROOT), 0) <= 1) continue;
            String sourceNodeId = nodeByRef.get(input.ref());
            String renamedTable = internalTable("source_" + input.ref(), tableByRef.values());
            String renameNodeId = uuid();
            nodes.add(new RenameNodeDefinition(
                    renameNodeId,
                    input.name() + "（区分逻辑表）",
                    layout(1, yByRef.get(input.ref())),
                    new RenameConfiguration(logicalTable, renamedTable, List.of())
            ));
            edges.add(edge(sourceNodeId, renameNodeId));
            nodeByRef.put(input.ref(), renameNodeId);
            tableByRef.put(input.ref(), renamedTable);
            depthByRef.put(input.ref(), 1);
        }

        for (TaskCanvasPlan.Step step : plan.steps()) {
            int depth = step.inputRefs().stream().mapToInt(depthByRef::get).max().orElse(0) + 1;
            double y = step.inputRefs().stream().mapToDouble(yByRef::get).average().orElse(START_Y);
            String outputTable = internalTable("ai_" + step.ref(), tableByRef.values());
            String nodeId = uuid();
            List<String> inputTables = step.inputRefs().stream().map(tableByRef::get).toList();
            List<List<String>> inputFields = step.inputRefs().stream().map(fieldsByRef::get).toList();
            List<String> outputFields = validateStepFields(step, inputFields, inputTables);
            CanvasNodeDefinition node = buildStep(
                    step, nodeId, depth, y, inputTables, inputFields, outputTable);
            nodes.add(node);
            for (String inputRef : step.inputRefs()) edges.add(edge(nodeByRef.get(inputRef), nodeId));
            nodeByRef.put(step.ref(), nodeId);
            tableByRef.put(step.ref(), outputTable);
            fieldsByRef.put(step.ref(), outputFields);
            depthByRef.put(step.ref(), depth);
            yByRef.put(step.ref(), y);
        }

        TaskCanvasPlan.Output output = plan.output();
        validateOutputFields(output, fieldsByRef.get(output.inputRef()), outputSchema);
        int outputDepth = depthByRef.get(output.inputRef()) + 1;
        double outputY = yByRef.get(output.inputRef());
        String outputId = uuid();
        CanvasNodeDefinition outputNode = switch (output.type()) {
            case MODEL_OUTPUT -> new ModelOutputNodeDefinition(
                    outputId, output.name(), layout(outputDepth, outputY),
                    new ModelOutputConfiguration(
                            List.of(new ModelOutputWrite(uuid(), tableByRef.get(output.inputRef()),
                                    output.modelId() == null ? "" : output.modelId().toString(),
                                    output.writeMode(), output.columnMappings()))
                    )
            );
            case JDBC_OUTPUT -> new JdbcOutputNodeDefinition(
                    outputId, output.name(), layout(outputDepth, outputY),
                    new JdbcOutputConfiguration(
                            output.dataSourceId() == null ? "" : output.dataSourceId().toString(),
                            List.of(new JdbcOutputWrite(uuid(), tableByRef.get(output.inputRef()),
                                    output.targetTableName() == null ? "" : output.targetTableName(),
                                    output.writeMode(), output.columnMappings(), output.upsertKeyColumns()))
                    )
            );
        };
        nodes.add(outputNode);
        edges.add(edge(nodeByRef.get(output.inputRef()), outputId));
        if (nodes.size() > MAX_NODES) throw badRequest("生成后的 Canvas 不能超过 " + MAX_NODES + " 个节点");

        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                nodes,
                edges
        );
        validator.validate(definition);
        return definition;
    }

    private static CanvasNodeDefinition buildStep(
            TaskCanvasPlan.Step step,
            String id,
            int depth,
            double y,
            List<String> inputTables,
            List<List<String>> inputFields,
            String outputTable
    ) {
        CanvasNodeLayout layout = layout(depth, y);
        String source = inputTables.getFirst();
        return switch (step.type()) {
            case FILTER -> new FilterNodeDefinition(
                    id, step.name(), layout, new FilterConfiguration(source, outputTable, step.filterCondition())
            );
            case SELECT_COLUMNS -> new SelectColumnsNodeDefinition(
                    id, step.name(), layout, new SelectColumnsConfiguration(source, outputTable, step.columns())
            );
            case RENAME -> new RenameNodeDefinition(
                    id, step.name(), layout, new RenameConfiguration(source, outputTable, step.renameMappings())
            );
            case TYPE_CAST -> new TypeCastNodeDefinition(
                    id, step.name(), layout, new TypeCastConfiguration(source, outputTable, step.casts())
            );
            case NULL_HANDLING -> new NullHandlingNodeDefinition(
                    id, step.name(), layout,
                    new NullHandlingConfiguration(source, outputTable, step.nullHandlingRules())
            );
            case DEDUPLICATE -> new DeduplicateNodeDefinition(
                    id, step.name(), layout, new DeduplicateConfiguration(
                    source, outputTable, step.deduplicateKeyColumns(), step.deduplicateKeepStrategy(),
                    step.deduplicateOrderBy()
            ));
            case JOIN -> new JoinNodeDefinition(
                    id, step.name(), layout, new JoinConfiguration(
                    inputTables.get(0), inputTables.get(1), outputTable, step.joinType(), step.joinConditions(),
                    suggestedJoinOutputColumns(inputTables.get(1), inputFields.get(0), inputFields.get(1))
            ));
            case AGGREGATE -> new AggregateNodeDefinition(
                    id, step.name(), layout, new AggregateConfiguration(
                    source, outputTable, step.groupByColumns(), step.aggregations()
            ));
        };
    }

    private static void validatePlan(
            TaskCanvasPlan plan,
            Map<String, AssistantTaskCanvasQueryService.ResourceFingerprint> resources
    ) {
        if (plan == null || plan.target() == null) throw badRequest("Canvas 计划目标不能为空");
        if ((plan.target().taskId() == null) == (plan.target().newTask() == null)) {
            throw badRequest("Canvas 计划必须且只能指定已有任务或新任务草稿");
        }
        if (plan.inputs().isEmpty() || plan.inputs().size() > 2) throw badRequest("Canvas 计划必须包含 1 到 2 个输入");
        if (plan.output() == null) throw badRequest("Canvas 计划必须包含一个输出");
        Set<String> refs = new HashSet<>();
        int joins = 0;
        for (TaskCanvasPlan.Input input : plan.inputs()) {
            requireRef(input.ref(), refs);
            requireName(input.name(), "输入名称");
            if (input.type() == null || !resources.containsKey(input.ref())) throw badRequest("输入资源未解析");
        }
        for (TaskCanvasPlan.Step step : plan.steps()) {
            requireRef(step.ref(), refs);
            requireName(step.name(), "处理步骤名称");
            if (step.type() == null) throw badRequest("处理步骤类型不能为空");
            int requiredInputs = step.type() == TaskCanvasPlan.StepType.JOIN ? 2 : 1;
            if (step.inputRefs().size() != requiredInputs || step.inputRefs().stream().anyMatch(ref -> !refs.contains(ref))) {
                throw badRequest("处理步骤输入引用无效或拓扑顺序错误：" + step.ref());
            }
            if (step.type() == TaskCanvasPlan.StepType.JOIN && ++joins > 1) throw badRequest("第一版最多支持一个 Join");
        }
        if (!refs.contains(plan.output().inputRef())) throw badRequest("输出引用了不存在的上游步骤");
        requireName(plan.output().name(), "输出名称");
        if (plan.output().type() == null) throw badRequest("输出类型不能为空");
        if (plan.inputs().size() + plan.steps().size() + 1 > MAX_NODES) {
            throw badRequest("Canvas 计划不能超过 " + MAX_NODES + " 个节点");
        }
    }

    private static List<String> validateStepFields(
            TaskCanvasPlan.Step step,
            List<List<String>> inputFields,
            List<String> inputTables
    ) {
        List<Set<String>> inputs = inputFields.stream()
                .map(fields -> (Set<String>) new java.util.LinkedHashSet<>(fields))
                .toList();
        Set<String> result = new java.util.LinkedHashSet<>(inputs.getFirst());
        switch (step.type()) {
            case FILTER -> validateFilterFields(step.filterCondition(), result, step.name());
            case SELECT_COLUMNS -> {
                requireFields(result, step.columns(), step.name());
                result = new java.util.LinkedHashSet<>(step.columns());
            }
            case RENAME -> {
                for (RenameColumnMapping mapping : step.renameMappings()) {
                    requireField(result, mapping.sourceColumnName(), step.name());
                    result.remove(mapping.sourceColumnName());
                    result.add(mapping.targetColumnName());
                }
            }
            case TYPE_CAST -> {
                for (ColumnTypeCast cast : step.casts()) {
                    requireField(result, cast.columnName(), step.name());
                }
            }
            case NULL_HANDLING -> {
                for (NullHandlingRule rule : step.nullHandlingRules()) {
                    switch (rule) {
                        case DropNullRowsRule drop -> requireFields(result, drop.columnNames(), step.name());
                        case FillNullLiteralRule fill -> requireField(result, fill.columnName(), step.name());
                    }
                }
            }
            case DEDUPLICATE -> {
                requireFields(result, step.deduplicateKeyColumns(), step.name());
                for (SortField sort : step.deduplicateOrderBy()) {
                    requireField(result, sort.columnName(), step.name());
                }
            }
            case JOIN -> {
                Set<String> right = inputs.get(1);
                for (JoinCondition condition : step.joinConditions()) {
                    requireField(result, condition.leftColumnName(), step.name() + "左表");
                    requireField(right, condition.rightColumnName(), step.name() + "右表");
                }
                return suggestedJoinOutputColumns(
                        inputTables.get(1), inputFields.get(0), inputFields.get(1)).stream()
                        .map(JoinOutputColumn::outputColumnName)
                        .toList();
            }
            case AGGREGATE -> {
                requireFields(result, step.groupByColumns(), step.name());
                java.util.LinkedHashSet<String> aggregated = new java.util.LinkedHashSet<>(step.groupByColumns());
                for (AggregateItem item : step.aggregations()) {
                    if (item.sourceColumnName() != null && !item.sourceColumnName().isBlank()) {
                        requireField(result, item.sourceColumnName(), step.name());
                    }
                    aggregated.add(item.outputColumnName());
                }
                result = aggregated;
            }
        }
        return List.copyOf(result);
    }

    private static void validateOutputFields(
            TaskCanvasPlan.Output output,
            List<String> sourceFieldList,
            AssistantTaskCanvasQueryService.SafeSchema outputSchema
    ) {
        Set<String> sourceFields = new java.util.LinkedHashSet<>(sourceFieldList);
        Set<String> targetFields = outputSchema.fields().stream()
                .map(AssistantTaskCanvasQueryService.SafeField::code)
                .collect(java.util.stream.Collectors.toSet());
        for (JdbcColumnMapping mapping : output.columnMappings()) {
            requireField(sourceFields, mapping.sourceColumnName(), output.name());
            requireField(targetFields, mapping.targetColumnName(), output.name() + "目标资源");
        }
        if (output.type() == TaskCanvasPlan.OutputType.JDBC_OUTPUT) {
            requireFields(targetFields, output.upsertKeyColumns(), output.name() + "目标资源");
        }
    }

    private static List<JoinOutputColumn> suggestedJoinOutputColumns(
            String rightTableName,
            List<String> leftFields,
            List<String> rightFields
    ) {
        Set<String> leftNames = leftFields.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<JoinOutputColumn> result = new ArrayList<>(leftFields.size() + rightFields.size());
        leftFields.forEach(field -> result.add(new JoinOutputColumn(
                JoinOutputColumnSource.LEFT, field, field, true)));
        rightFields.forEach(field -> result.add(new JoinOutputColumn(
                JoinOutputColumnSource.RIGHT,
                field,
                leftNames.contains(field.toLowerCase(Locale.ROOT)) ? rightTableName + "_" + field : field,
                true
        )));
        return List.copyOf(result);
    }

    private static void validateFilterFields(CanvasFilterCondition condition, Set<String> fields, String stepName) {
        if (condition == null) return;
        switch (condition) {
            case CanvasFieldPredicate predicate -> requireField(fields, predicate.columnName(), stepName);
            case CanvasFilterGroup group -> group.children().forEach(child -> validateFilterFields(child, fields, stepName));
        }
    }

    private static void requireFields(Set<String> available, List<String> requested, String context) {
        if (requested == null) return;
        requested.forEach(field -> requireField(available, field, context));
    }

    private static void requireField(Set<String> available, String field, String context) {
        if (field == null || field.isBlank() || !available.contains(field)) {
            throw badRequest(context + " 引用了不存在的字段：" + (field == null ? "" : field));
        }
    }

    private static void requireRef(String value, Set<String> refs) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]{0,79}")) {
            throw badRequest("计划引用必须以字母开头且不超过 80 个字符");
        }
        if (!refs.add(value)) throw badRequest("计划引用重复：" + value);
    }

    private static void requireName(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 100) throw badRequest(label + "长度必须为 1 到 100");
    }

    private static String internalTable(String seed, java.util.Collection<String> existing) {
        String base = safeTableName(seed, "ai_table");
        Set<String> lower = existing.stream().map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String candidate = base;
        int suffix = 2;
        while (lower.contains(candidate.toLowerCase(Locale.ROOT))) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static String safeTableName(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = fallback;
        if (!Character.isLetter(normalized.charAt(0))) normalized = "t_" + normalized;
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96);
    }

    private static CanvasNodeLayout layout(int depth, double y) {
        return new CanvasNodeLayout(START_X + depth * X_GAP, y, WIDTH, HEIGHT);
    }

    private static CanvasEdgeDefinition edge(String source, String target) {
        return new CanvasEdgeDefinition(uuid(), source, target);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
