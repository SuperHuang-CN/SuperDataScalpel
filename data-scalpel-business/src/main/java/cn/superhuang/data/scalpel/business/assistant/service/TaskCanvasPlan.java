package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.contract.task.AggregateItem;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.DeduplicateKeepStrategy;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.NullHandlingRule;
import cn.superhuang.data.scalpel.contract.task.RenameColumnMapping;
import cn.superhuang.data.scalpel.contract.task.SortField;

import java.util.List;
import java.util.UUID;

/** Assistant-only semantic plan. Node IDs, edges, layouts and table wiring are intentionally absent. */
public record TaskCanvasPlan(
        Target target,
        List<Input> inputs,
        List<Step> steps,
        Output output,
        String summary,
        List<String> assumptions,
        List<String> needsUserInput
) {
    public TaskCanvasPlan {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        steps = steps == null ? List.of() : List.copyOf(steps);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        needsUserInput = needsUserInput == null ? List.of() : List.copyOf(needsUserInput);
    }

    public record Target(UUID taskId, NewTaskDraft newTask) {}

    public record NewTaskDraft(String name, UUID directoryId, String description) {}

    public enum InputType { MODEL, JDBC_TABLE, FILE_DATASET_TABLE }

    public record Input(
            String ref,
            String name,
            InputType type,
            UUID modelId,
            UUID dataSourceId,
            String tableName,
            UUID fileDatasetId,
            UUID fileDatasetTableId
    ) {}

    public enum StepType {
        FILTER,
        SELECT_COLUMNS,
        RENAME,
        TYPE_CAST,
        NULL_HANDLING,
        DEDUPLICATE,
        JOIN,
        AGGREGATE
    }

    public record Step(
            String ref,
            String name,
            StepType type,
            List<String> inputRefs,
            CanvasFilterCondition filterCondition,
            List<String> columns,
            List<RenameColumnMapping> renameMappings,
            List<ColumnTypeCast> casts,
            List<NullHandlingRule> nullHandlingRules,
            List<String> deduplicateKeyColumns,
            DeduplicateKeepStrategy deduplicateKeepStrategy,
            List<SortField> deduplicateOrderBy,
            JoinType joinType,
            List<JoinCondition> joinConditions,
            List<String> groupByColumns,
            List<AggregateItem> aggregations
    ) {
        public Step {
            inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
            columns = columns == null ? List.of() : List.copyOf(columns);
            renameMappings = renameMappings == null ? List.of() : List.copyOf(renameMappings);
            casts = casts == null ? List.of() : List.copyOf(casts);
            nullHandlingRules = nullHandlingRules == null ? List.of() : List.copyOf(nullHandlingRules);
            deduplicateKeyColumns = deduplicateKeyColumns == null ? List.of() : List.copyOf(deduplicateKeyColumns);
            deduplicateOrderBy = deduplicateOrderBy == null ? List.of() : List.copyOf(deduplicateOrderBy);
            joinConditions = joinConditions == null ? List.of() : List.copyOf(joinConditions);
            groupByColumns = groupByColumns == null ? List.of() : List.copyOf(groupByColumns);
            aggregations = aggregations == null ? List.of() : List.copyOf(aggregations);
        }
    }

    public enum OutputType { MODEL_OUTPUT, JDBC_OUTPUT }

    public record Output(
            String name,
            String inputRef,
            OutputType type,
            UUID modelId,
            UUID dataSourceId,
            String targetTableName,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> columnMappings,
            List<String> upsertKeyColumns
    ) {
        public Output {
            columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
            upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
        }
    }
}
