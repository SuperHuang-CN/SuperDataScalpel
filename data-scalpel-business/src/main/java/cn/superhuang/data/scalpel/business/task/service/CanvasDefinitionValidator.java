package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasExpressionLimits;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Validates only the stable structure needed to persist and safely reload an incomplete Canvas draft. */
@Component
public class CanvasDefinitionValidator {

    private static final Pattern SENSITIVE_RUNTIME_PARAMETER = Pattern.compile(
            "(?i).*(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature).*"
    );
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final CanvasDefinitionUpgrader upgrader;

    public CanvasDefinitionValidator(CanvasDefinitionUpgrader upgrader) {
        this.upgrader = upgrader;
    }

    public void validate(CanvasDefinition definition) {
        if (definition == null) {
            invalid("Canvas 定义不能为空");
        }
        upgrader.requireSupportedSource(definition);
        if (definition.nodes() == null || definition.edges() == null) {
            invalid("Canvas nodes 和 edges 必须是数组");
        }
        Set<String> nodeIds = new HashSet<>();
        for (int index = 0; index < definition.nodes().size(); index++) {
            CanvasNodeDefinition node = definition.nodes().get(index);
            if (node == null) {
                invalid("nodes[" + index + "] 不能为空");
            }
            String path = "nodes[" + index + "]";
            if (node.nodeType().introducedInMinorVersion() > definition.effectiveSchemaMinorVersion()) {
                invalid(path + ".type 从 Canvas " + CanvasDefinition.CURRENT_SCHEMA_VERSION + "."
                        + node.nodeType().introducedInMinorVersion() + " 开始支持");
            }
            requireUuid(node.id(), path + ".id");
            if (!nodeIds.add(node.id())) {
                invalid("节点 ID " + node.id() + " 重复");
            }
            requireName(node.name(), path + ".name");
            validateLayout(node.layout(), path + ".layout");
            validateConfiguration(node, path + ".configuration");
        }

        Set<String> edgeIds = new HashSet<>();
        for (int index = 0; index < definition.edges().size(); index++) {
            CanvasEdgeDefinition edge = definition.edges().get(index);
            if (edge == null) {
                invalid("edges[" + index + "] 不能为空");
            }
            String path = "edges[" + index + "]";
            requireUuid(edge.id(), path + ".id");
            requireUuid(edge.sourceNodeId(), path + ".sourceNodeId");
            requireUuid(edge.targetNodeId(), path + ".targetNodeId");
            if (!edgeIds.add(edge.id())) {
                invalid("连线 ID " + edge.id() + " 重复");
            }
            if (!nodeIds.contains(edge.sourceNodeId()) || !nodeIds.contains(edge.targetNodeId())) {
                invalid("连线 " + edge.id() + " 引用了不存在的节点");
            }
        }
    }

    private static void validateConfiguration(CanvasNodeDefinition node, String path) {
        if (node instanceof RenameNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof FilterNodeDefinition value) {
            validateFilterConfiguration(value.configuration(), path);
            return;
        }
        if (node instanceof SelectColumnsNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof DeriveColumnsNodeDefinition value) {
            validateDeriveColumnsConfiguration(value.configuration(), path);
            return;
        }
        if (node instanceof TypeCastNodeDefinition value) {
            validateTypeCastConfiguration(value.configuration(), path);
            return;
        }
        if (node instanceof DeduplicateNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof NullHandlingNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof ValueMappingNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof MaskFieldsNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof JsonExtractNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        if (node instanceof TopNNodeDefinition value) {
            validateProcessorOperations(value.configuration() == null ? null : value.configuration().operations(), path);
            return;
        }
        switch (node) {
            case ModelInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                Set<String> modelIds = new HashSet<>();
                for (int index = 0; index < input.configuration().models().size(); index++) {
                    ModelInputSelection selection = input.configuration().models().get(index);
                    if (selection == null) invalid(path + ".models[" + index + "] 不能为空");
                    requireOptionalUuid(selection.modelId(), path + ".models[" + index + "].modelId");
                    if (!selection.modelId().isBlank() && !modelIds.add(selection.modelId())) {
                        invalid(path + ".models[" + index + "].modelId 重复");
                    }
                }
            }
            case JdbcInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
                if (input.configuration().tables() == null) {
                    invalid(path + ".tables 必须是数组");
                }
                for (int index = 0; index < input.configuration().tables().size(); index++) {
                    JdbcInputTableSelection table = input.configuration().tables().get(index);
                    if (table == null) invalid(path + ".tables[" + index + "] 不能为空");
                    requireString(table.tableName(), path + ".tables[" + index + "].tableName");
                    if (table.readOptions() == null) {
                        invalid(path + ".tables[" + index + "].readOptions 必须是数组");
                    }
                    for (int optionIndex = 0; optionIndex < table.readOptions().size(); optionIndex++) {
                        JdbcInputReadOption option = table.readOptions().get(optionIndex);
                        if (option == null || option.name() == null || option.value() == null) {
                            invalid(path + ".tables[" + index + "].readOptions[" + optionIndex
                                    + "] 必须包含 name 和 value");
                        }
                    }
                }
            }
            case JdbcIncrementalInputNodeDefinition input -> {
                JdbcIncrementalInputConfiguration configuration = input.configuration();
                if (configuration == null) invalid(path + " 不能为空");
                requireOptionalUuid(configuration.dataSourceId(), path + ".dataSourceId");
                requireString(configuration.tableName(), path + ".tableName");
                requireString(configuration.outputTableName(), path + ".outputTableName");
                requireString(configuration.incrementalTimeColumn(), path + ".incrementalTimeColumn");
                if (configuration.startPosition() == null) {
                    invalid(path + ".startPosition 不能为空");
                }
                if (configuration.startPosition() == JdbcIncrementalStartPosition.AT_TIME
                        && configuration.startTime() == null) {
                    invalid(path + ".startTime 在 AT_TIME 模式下不能为空");
                }
                requireString(configuration.cursorTimeZone(), path + ".cursorTimeZone");
                try {
                    ZoneId.of(configuration.cursorTimeZone());
                } catch (DateTimeException exception) {
                    invalid(path + ".cursorTimeZone 不是有效时区");
                }
                if (configuration.visibilityDelaySeconds() == null
                        || configuration.visibilityDelaySeconds() < 0
                        || configuration.visibilityDelaySeconds() > 3600) {
                    invalid(path + ".visibilityDelaySeconds 必须在 0 到 3600 之间");
                }
                validateTriggerInterval(configuration.triggerIntervalSeconds(), path + ".triggerIntervalSeconds");
            }
            case JdbcQueryInputNodeDefinition input -> {
                JdbcQueryInputConfiguration configuration = input.configuration();
                if (configuration == null) invalid(path + " 不能为空");
                requireOptionalUuid(configuration.dataSourceId(), path + ".dataSourceId");
                requireString(configuration.sql(), path + ".sql");
                if (configuration.sql().length() > 100_000) {
                    invalid(path + ".sql 不能超过 100000 个字符");
                }
                requireString(configuration.outputTableName(), path + ".outputTableName");
                if (configuration.analyzedSqlSha256() != null
                        && !configuration.analyzedSqlSha256().isBlank()
                        && !SHA_256.matcher(configuration.analyzedSqlSha256()).matches()) {
                    invalid(path + ".analyzedSqlSha256 必须是 64 位小写 SHA-256");
                }
                if (configuration.outputColumns() == null) {
                    invalid(path + ".outputColumns 必须是数组");
                }
                Set<String> columnNames = new HashSet<>();
                for (int index = 0; index < configuration.outputColumns().size(); index++) {
                    CanvasColumnSchema column = configuration.outputColumns().get(index);
                    if (column == null || column.fieldType() == null) {
                        invalid(path + ".outputColumns[" + index + "] 不完整");
                    }
                    requireString(column.name(), path + ".outputColumns[" + index + "].name");
                    if (!columnNames.add(column.name())) {
                        invalid(path + ".outputColumns 字段名不能重复: " + column.name());
                    }
                }
            }
            case FileDatasetInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().fileDatasetId(), path + ".fileDatasetId");
                Set<String> tableIds = new HashSet<>();
                for (int index = 0; index < input.configuration().tables().size(); index++) {
                    FileDatasetInputTableSelection selection = input.configuration().tables().get(index);
                    if (selection == null) invalid(path + ".tables[" + index + "] 不能为空");
                    requireOptionalUuid(selection.fileDatasetTableId(), path + ".tables[" + index + "].fileDatasetTableId");
                    if (!selection.fileDatasetTableId().isBlank() && !tableIds.add(selection.fileDatasetTableId())) {
                        invalid(path + ".tables[" + index + "].fileDatasetTableId 重复");
                    }
                }
            }
            case HttpApiInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
                Set<String> resourceIds = new HashSet<>();
                for (int index = 0; index < input.configuration().resources().size(); index++) {
                    HttpApiInputResourceSelection resource = input.configuration().resources().get(index);
                    String resourcePath = path + ".resources[" + index + "]";
                    if (resource == null) invalid(resourcePath + " 不能为空");
                    requireOptionalUuid(resource.resourceId(), resourcePath + ".resourceId");
                    requireString(resource.outputTableName(), resourcePath + ".outputTableName");
                    if (!resource.resourceId().isBlank() && !resourceIds.add(resource.resourceId())) {
                        invalid(resourcePath + ".resourceId 重复");
                    }
                    Set<String> names = new HashSet<>();
                    for (int parameterIndex = 0; parameterIndex < resource.runtimeParameters().size(); parameterIndex++) {
                        var parameter = resource.runtimeParameters().get(parameterIndex);
                        if (parameter == null || parameter.name() == null || parameter.value() == null
                                || !parameter.name().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")
                                || SENSITIVE_RUNTIME_PARAMETER.matcher(parameter.name()).matches()
                                || !names.add(parameter.name())) {
                            invalid(resourcePath + ".runtimeParameters[" + parameterIndex + "] 无效或名称重复");
                        }
                    }
                }
            }
            case SpatialServiceInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
                Set<String> resourceIds = new HashSet<>();
                for (int index = 0; index < input.configuration().resources().size(); index++) {
                    SpatialServiceInputResourceSelection resource = input.configuration().resources().get(index);
                    String resourcePath = path + ".resources[" + index + "]";
                    if (resource == null) invalid(resourcePath + " 不能为空");
                    requireOptionalUuid(resource.resourceId(), resourcePath + ".resourceId");
                    requireString(resource.outputTableName(), resourcePath + ".outputTableName");
                    if (!resource.resourceId().isBlank() && !resourceIds.add(resource.resourceId())) {
                        invalid(resourcePath + ".resourceId 重复");
                    }
                }
            }
            case KafkaInputNodeDefinition input -> {
                validateKafkaInputConfiguration(input.configuration(), path);
            }
            case TdEngineTmqInputNodeDefinition input -> {
                TdEngineTmqInputConfiguration configuration = input.configuration();
                if (configuration == null) invalid(path + " 不能为空");
                requireOptionalUuid(configuration.dataSourceId(), path + ".dataSourceId");
                requireString(configuration.topicName(), path + ".topicName");
                requireString(configuration.catalogName(), path + ".catalogName");
                requireString(configuration.supertableName(), path + ".supertableName");
                requireString(configuration.topicDefinitionFingerprint(), path + ".topicDefinitionFingerprint");
                if (!SHA_256.matcher(configuration.topicDefinitionFingerprint()).matches()) {
                    invalid(path + ".topicDefinitionFingerprint 必须是 64 位小写 SHA-256");
                }
                requireString(configuration.outputTableName(), path + ".outputTableName");
                if (configuration.startingOffsets() == null) {
                    invalid(path + ".startingOffsets 不能为空");
                }
                if (configuration.maxOffsetsPerVGroupPerTrigger() == null
                        || configuration.maxOffsetsPerVGroupPerTrigger() < 1
                        || configuration.maxOffsetsPerVGroupPerTrigger()
                        > TdEngineTmqInputConfiguration.MAX_OFFSETS_PER_VGROUP_PER_TRIGGER) {
                    invalid(path + ".maxOffsetsPerVGroupPerTrigger 必须在 1 到 1000000 之间");
                }
                validateTriggerInterval(
                        configuration.triggerIntervalSeconds(),
                        path + ".triggerIntervalSeconds"
                );
            }
            case JoinNodeDefinition join -> {
                if (join.configuration() == null) invalid(path + " 不能为空");
                requireString(join.configuration().leftTableName(), path + ".leftTableName");
                requireString(join.configuration().rightTableName(), path + ".rightTableName");
                requireString(join.configuration().outputTableName(), path + ".outputTableName");
                List<JoinCondition> conditions = join.configuration().conditions();
                if (conditions == null) invalid(path + ".conditions 必须是数组");
                for (int index = 0; index < conditions.size(); index++) {
                    JoinCondition condition = conditions.get(index);
                    if (condition == null || condition.operator() == null) {
                        invalid(path + ".conditions[" + index + "] 不完整");
                    }
                    requireString(condition.leftColumnName(), path + ".conditions[" + index + "].leftColumnName");
                    requireString(condition.rightColumnName(), path + ".conditions[" + index + "].rightColumnName");
                }
                List<JoinOutputColumn> outputColumns = join.configuration().outputColumns();
                if (outputColumns == null) invalid(path + ".outputColumns 必须是数组");
                for (int index = 0; index < outputColumns.size(); index++) {
                    JoinOutputColumn outputColumn = outputColumns.get(index);
                    if (outputColumn == null) {
                        invalid(path + ".outputColumns[" + index + "] 不能为空");
                    }
                }
            }
            case GeometryConstructNodeDefinition construct ->
                    validateGeometryConstruct(construct.configuration(), path);
            case SpatialTransformNodeDefinition transform -> {
                if (transform.configuration() == null) invalid(path + " 不能为空");
                requireString(transform.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(transform.configuration().outputTableName(), path + ".outputTableName");
                requireString(transform.configuration().geometryColumnName(), path + ".geometryColumnName");
            }
            case SpatialJoinNodeDefinition join -> {
                if (join.configuration() == null) invalid(path + " 不能为空");
                requireString(join.configuration().leftTableName(), path + ".leftTableName");
                requireString(join.configuration().rightTableName(), path + ".rightTableName");
                requireString(join.configuration().outputTableName(), path + ".outputTableName");
                List<SpatialJoinCondition> conditions = join.configuration().conditions();
                if (conditions == null) invalid(path + ".conditions 必须是数组");
                if (conditions.size() > SpatialJoinConfiguration.MAX_CONDITIONS) {
                    invalid(path + ".conditions 不能超过 "
                            + SpatialJoinConfiguration.MAX_CONDITIONS + " 项");
                }
                for (int index = 0; index < conditions.size(); index++) {
                    SpatialJoinCondition condition = conditions.get(index);
                    String conditionPath = path + ".conditions[" + index + "]";
                    if (condition == null || condition.predicate() == null) {
                        invalid(conditionPath + " 不完整");
                    }
                    requireString(
                            condition.leftGeometryColumnName(),
                            conditionPath + ".leftGeometryColumnName"
                    );
                    requireString(
                            condition.rightGeometryColumnName(),
                            conditionPath + ".rightGeometryColumnName"
                    );
                }
            }
            case GeometryValidateNodeDefinition validate ->
                    validateGeometryValidate(validate.configuration(), path);
            case GeometryRepairNodeDefinition repair -> {
                if (repair.configuration() == null) invalid(path + " 不能为空");
                requireString(repair.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(repair.configuration().outputTableName(), path + ".outputTableName");
                requireString(repair.configuration().geometryColumnName(), path + ".geometryColumnName");
                requireString(repair.configuration().outputColumnName(), path + ".outputColumnName");
            }
            case GeometryBufferNodeDefinition buffer -> {
                if (buffer.configuration() == null) invalid(path + " 不能为空");
                requireString(buffer.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(buffer.configuration().outputTableName(), path + ".outputTableName");
                requireString(buffer.configuration().geometryColumnName(), path + ".geometryColumnName");
                requireString(buffer.configuration().outputColumnName(), path + ".outputColumnName");
                if (!Double.isFinite(buffer.configuration().distance())
                        || buffer.configuration().distance() <= 0) {
                    invalid(path + ".distance 必须是有限正数");
                }
                if (buffer.configuration().mode() == null) {
                    invalid(path + ".mode 不能为空");
                }
            }
            case GeometryExplodeNodeDefinition explode -> {
                if (explode.configuration() == null) invalid(path + " 不能为空");
                requireString(explode.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(explode.configuration().outputTableName(), path + ".outputTableName");
                requireString(explode.configuration().geometryColumnName(), path + ".geometryColumnName");
                requireString(explode.configuration().outputColumnName(), path + ".outputColumnName");
                if (explode.configuration().partIndexColumnName() != null) {
                    requireString(
                            explode.configuration().partIndexColumnName(),
                            path + ".partIndexColumnName"
                    );
                }
            }
            case SpatialMeasureNodeDefinition measure ->
                    validateSpatialMeasure(measure.configuration(), path);
            case GeometrySerializeNodeDefinition serialize ->
                    validateGeometrySerialize(serialize.configuration(), path);
            case SpatialClipNodeDefinition clip -> {
                if (clip.configuration() == null) invalid(path + " 不能为空");
                requireString(clip.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(clip.configuration().maskTableName(), path + ".maskTableName");
                requireString(clip.configuration().outputTableName(), path + ".outputTableName");
                requireString(
                        clip.configuration().sourceGeometryColumnName(),
                        path + ".sourceGeometryColumnName"
                );
                requireString(
                        clip.configuration().maskGeometryColumnName(),
                        path + ".maskGeometryColumnName"
                );
                requireString(clip.configuration().outputColumnName(), path + ".outputColumnName");
            }
            case SpatialAggregateNodeDefinition aggregate -> {
                if (aggregate.configuration() == null) invalid(path + " 不能为空");
                requireString(
                        aggregate.configuration().sourceTableName(),
                        path + ".sourceTableName"
                );
                requireString(
                        aggregate.configuration().outputTableName(),
                        path + ".outputTableName"
                );
                if (aggregate.configuration().groupByColumns() == null) {
                    invalid(path + ".groupByColumns 必须是数组");
                }
                for (int index = 0;
                     index < aggregate.configuration().groupByColumns().size(); index++) {
                    requireString(
                            aggregate.configuration().groupByColumns().get(index),
                            path + ".groupByColumns[" + index + "]"
                    );
                }
                if (aggregate.configuration().aggregations() == null) {
                    invalid(path + ".aggregations 必须是数组");
                }
                if (aggregate.configuration().aggregations().size()
                        > SpatialAggregateConfiguration.MAX_AGGREGATIONS) {
                    invalid(path + ".aggregations 不能超过 "
                            + SpatialAggregateConfiguration.MAX_AGGREGATIONS + " 项");
                }
                for (int index = 0;
                     index < aggregate.configuration().aggregations().size(); index++) {
                    SpatialAggregation item = aggregate.configuration().aggregations().get(index);
                    String itemPath = path + ".aggregations[" + index + "]";
                    if (item == null) invalid(itemPath + " 不能为空");
                    if (item.kind() == null) invalid(itemPath + ".kind 不能为空");
                    requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
                    requireString(item.outputColumnName(), itemPath + ".outputColumnName");
                }
            }
            case StreamJoinNodeDefinition join -> {
                if (join.configuration() == null) invalid(path + " 不能为空");
                requireString(join.configuration().leftTableName(), path + ".leftTableName");
                requireString(join.configuration().rightTableName(), path + ".rightTableName");
                requireString(join.configuration().outputTableName(), path + ".outputTableName");
                validateJoinConditions(join.configuration().conditions(), path + ".conditions");
                List<JoinOutputColumn> outputColumns = join.configuration().outputColumns();
                if (outputColumns == null) invalid(path + ".outputColumns 必须是数组");
                for (int index = 0; index < outputColumns.size(); index++) {
                    if (outputColumns.get(index) == null) {
                        invalid(path + ".outputColumns[" + index + "] 不能为空");
                    }
                }
            }
            case RenameNodeDefinition rename -> {
                if (rename.configuration() == null) invalid(path + " 不能为空");
                requireString(rename.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(rename.configuration().outputTableName(), path + ".outputTableName");
                List<RenameColumnMapping> mappings = rename.configuration().columnMappings();
                if (mappings == null) invalid(path + ".columnMappings 必须是数组");
                for (int index = 0; index < mappings.size(); index++) {
                    RenameColumnMapping mapping = mappings.get(index);
                    if (mapping == null) invalid(path + ".columnMappings[" + index + "] 不能为空");
                    requireString(mapping.sourceColumnName(),
                            path + ".columnMappings[" + index + "].sourceColumnName");
                    requireString(mapping.targetColumnName(),
                            path + ".columnMappings[" + index + "].targetColumnName");
                }
            }
            case FilterNodeDefinition filter -> {
                if (filter.configuration() == null) invalid(path + " 不能为空");
                requireString(filter.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(filter.configuration().outputTableName(), path + ".outputTableName");
                if (filter.configuration().condition() == null) {
                    invalid(path + ".condition 不能为空");
                }
                int[] conditionNodes = {0};
                validateFilterCondition(
                        filter.configuration().condition(),
                        path + ".condition",
                        1,
                        conditionNodes
                );
            }
            case SqlTransformNodeDefinition sqlTransform ->
                    validateSqlTransform(sqlTransform.configuration(), path);
            case SelectColumnsNodeDefinition selectColumns -> {
                if (selectColumns.configuration() == null) invalid(path + " 不能为空");
                requireString(selectColumns.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(selectColumns.configuration().outputTableName(), path + ".outputTableName");
                if (selectColumns.configuration().columns() == null) {
                    invalid(path + ".columns 必须是数组");
                }
                for (int index = 0; index < selectColumns.configuration().columns().size(); index++) {
                    requireString(
                            selectColumns.configuration().columns().get(index),
                            path + ".columns[" + index + "]"
                    );
                }
            }
            case DeriveColumnsNodeDefinition deriveColumns ->
                    validateDeriveColumnsConfiguration(deriveColumns.configuration(), path);
            case TypeCastNodeDefinition typeCast -> {
                if (typeCast.configuration() == null) invalid(path + " 不能为空");
                requireString(typeCast.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(typeCast.configuration().outputTableName(), path + ".outputTableName");
                List<ColumnTypeCast> casts = typeCast.configuration().casts();
                if (casts == null) invalid(path + ".casts 必须是数组");
                for (int index = 0; index < casts.size(); index++) {
                    ColumnTypeCast cast = casts.get(index);
                    String castPath = path + ".casts[" + index + "]";
                    if (cast == null) invalid(castPath + " 不能为空");
                    requireString(cast.columnName(), castPath + ".columnName");
                    if (cast.targetType() == null) invalid(castPath + ".targetType 不能为空");
                    if (cast.failureStrategy() == null) {
                        invalid(castPath + ".failureStrategy 不能为空");
                    }
                }
            }
            case AggregateNodeDefinition aggregate -> {
                if (aggregate.configuration() == null) invalid(path + " 不能为空");
                requireString(aggregate.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(aggregate.configuration().outputTableName(), path + ".outputTableName");
                if (aggregate.configuration().groupByColumns() == null) {
                    invalid(path + ".groupByColumns 必须是数组");
                }
                for (int index = 0; index < aggregate.configuration().groupByColumns().size(); index++) {
                    requireString(
                            aggregate.configuration().groupByColumns().get(index),
                            path + ".groupByColumns[" + index + "]"
                    );
                }
                if (aggregate.configuration().aggregations() == null) {
                    invalid(path + ".aggregations 必须是数组");
                }
                for (int index = 0; index < aggregate.configuration().aggregations().size(); index++) {
                    AggregateItem item =
                            aggregate.configuration().aggregations().get(index);
                    String itemPath = path + ".aggregations[" + index + "]";
                    if (item == null) invalid(itemPath + " 不能为空");
                    if (item.function() == null) invalid(itemPath + ".function 不能为空");
                    requireString(item.outputColumnName(), itemPath + ".outputColumnName");
                    if (item.sourceColumnName() != null) {
                        requireString(item.sourceColumnName(), itemPath + ".sourceColumnName");
                    }
                }
            }
            case UnionNodeDefinition union -> {
                if (union.configuration() == null) invalid(path + " 不能为空");
                if (union.configuration().inputTableNames() == null) {
                    invalid(path + ".inputTableNames 必须是数组");
                }
                for (int index = 0; index < union.configuration().inputTableNames().size(); index++) {
                    requireString(
                            union.configuration().inputTableNames().get(index),
                            path + ".inputTableNames[" + index + "]"
                    );
                }
                requireString(union.configuration().outputTableName(), path + ".outputTableName");
                if (union.configuration().mode() == null) invalid(path + ".mode 不能为空");
            }
            case DeduplicateNodeDefinition deduplicate -> {
                if (deduplicate.configuration() == null) invalid(path + " 不能为空");
                requireString(
                        deduplicate.configuration().sourceTableName(),
                        path + ".sourceTableName"
                );
                requireString(
                        deduplicate.configuration().outputTableName(),
                        path + ".outputTableName"
                );
                if (deduplicate.configuration().keyColumns() == null) {
                    invalid(path + ".keyColumns 必须是数组");
                }
                for (int index = 0; index < deduplicate.configuration().keyColumns().size(); index++) {
                    requireString(
                            deduplicate.configuration().keyColumns().get(index),
                            path + ".keyColumns[" + index + "]"
                    );
                }
                if (deduplicate.configuration().keepStrategy() == null) {
                    invalid(path + ".keepStrategy 不能为空");
                }
                if (deduplicate.configuration().orderBy() == null) {
                    invalid(path + ".orderBy 必须是数组");
                }
                for (int index = 0; index < deduplicate.configuration().orderBy().size(); index++) {
                    SortField sortField =
                            deduplicate.configuration().orderBy().get(index);
                    String sortPath = path + ".orderBy[" + index + "]";
                    if (sortField == null) invalid(sortPath + " 不能为空");
                    requireString(sortField.columnName(), sortPath + ".columnName");
                    if (sortField.direction() == null) {
                        invalid(sortPath + ".direction 不能为空");
                    }
                    if (sortField.nullOrdering() == null) {
                        invalid(sortPath + ".nullOrdering 不能为空");
                    }
                }
            }
            case NullHandlingNodeDefinition nullHandling ->
                    validateNullHandling(nullHandling.configuration(), path);
            case ValueMappingNodeDefinition valueMapping ->
                    validateValueMapping(valueMapping.configuration(), path);
            case MaskFieldsNodeDefinition maskFields ->
                    validateMaskFields(maskFields.configuration(), path);
            case JsonExtractNodeDefinition jsonExtract ->
                    validateJsonExtract(jsonExtract.configuration(), path);
            case WindowNodeDefinition window ->
                    validateWindow(window.configuration(), path);
            case TopNNodeDefinition topN ->
                    validateTopN(topN.configuration(), path);
            case JdbcOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                validateJdbcOutputWrites(output.configuration().writes(), path + ".writes");
            }
            case ModelOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                validateModelOutputWrites(output.configuration().writes(), path + ".writes");
            }
            case JdbcSnapshotSyncOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireString(output.configuration().sourceTableName(), path + ".sourceTableName");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                requireString(output.configuration().targetTableName(), path + ".targetTableName");
                validateSnapshotSyncConfiguration(output.configuration(), path);
            }
            case ModelSnapshotSyncOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireString(output.configuration().sourceTableName(), path + ".sourceTableName");
                requireOptionalUuid(output.configuration().targetModelId(), path + ".targetModelId");
                validateSnapshotSyncConfiguration(output.configuration(), path);
            }
            case KafkaOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                validateKafkaOutputWrites(output.configuration().writes(), path + ".writes");
            }
            case FileOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                validateFileOutputWrites(output.configuration().writes(), path + ".writes");
            }
        }
    }

    private static void validateJdbcOutputWrites(List<JdbcOutputWrite> writes, String path) {
        validateWriteList(writes, path);
        for (int index = 0; index < writes.size(); index++) {
            JdbcOutputWrite write = writes.get(index);
            String writePath = path + "[" + index + "]";
            requireString(write.sourceTableName(), writePath + ".sourceTableName");
            requireString(write.targetTableName(), writePath + ".targetTableName");
            validateMappings(write.columnMappings(), writePath + ".columnMappings");
            validateStringArray(write.upsertKeyColumns(), writePath + ".upsertKeyColumns");
        }
    }

    private static void validateModelOutputWrites(List<ModelOutputWrite> writes, String path) {
        validateWriteList(writes, path);
        for (int index = 0; index < writes.size(); index++) {
            ModelOutputWrite write = writes.get(index);
            String writePath = path + "[" + index + "]";
            requireString(write.sourceTableName(), writePath + ".sourceTableName");
            requireOptionalUuid(write.targetModelId(), writePath + ".targetModelId");
            validateMappings(write.columnMappings(), writePath + ".columnMappings");
        }
    }

    private static void validateKafkaOutputWrites(List<KafkaOutputWrite> writes, String path) {
        validateWriteList(writes, path);
        for (int index = 0; index < writes.size(); index++) {
            KafkaOutputWrite write = writes.get(index);
            String writePath = path + "[" + index + "]";
            requireString(write.sourceTableName(), writePath + ".sourceTableName");
            requireString(write.topic(), writePath + ".topic");
            if (write.legacyMappingMode()) {
                if (write.valueColumnNames() != null && !write.valueColumnNames().isEmpty()) {
                    invalid(writePath + ".valueColumnNames 在旧版 JSON 映射模式下必须为空");
                }
                validateKafkaValueSchema(write.valueSchema(), writePath + ".valueSchema");
                validateMappings(write.columnMappings(), writePath + ".columnMappings");
                continue;
            }
            if (write.valueSchema() != null
                    && write.valueSchema().columns() != null
                    && !write.valueSchema().columns().isEmpty()) {
                invalid(writePath + ".valueSchema.columns 在新 Kafka Value 模式下必须为空");
            }
            if (write.columnMappings() != null && !write.columnMappings().isEmpty()) {
                invalid(writePath + ".columnMappings 在新 Kafka Value 模式下必须为空");
            }
            validateKafkaOutputValueColumns(write, writePath);
        }
    }

    private static void validateKafkaOutputValueColumns(KafkaOutputWrite write, String path) {
        List<String> columns = write.valueColumnNames();
        if (columns == null) invalid(path + ".valueColumnNames 必须是数组");
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            if (column == null || column.isBlank()) {
                invalid(path + ".valueColumnNames[" + index + "] 不能为空");
            }
            if (!unique.add(column)) {
                invalid(path + ".valueColumnNames 中字段重复：" + column);
            }
        }
        int requiredCount = switch (write.valueFormat()) {
            case JSON -> -1;
            case TEXT, BINARY -> 1;
        };
        if (write.valueFormat() == cn.superhuang.data.scalpel.contract.task.KafkaOutputValueFormat.JSON
                && columns.isEmpty()) {
            invalid(path + ".valueColumnNames 至少需要一个 JSON 字段");
        }
        if (requiredCount == 1 && columns.size() != 1) {
            invalid(path + ".valueColumnNames 在 " + write.valueFormat() + " 格式下必须且只能有一个字段");
        }
    }

    private static void validateFileOutputWrites(List<FileOutputWrite> writes, String path) {
        validateWriteList(writes, path);
        for (int index = 0; index < writes.size(); index++) {
            FileOutputWrite write = writes.get(index);
            String writePath = path + "[" + index + "]";
            requireString(write.sourceTableName(), writePath + ".sourceTableName");
            validateFileOutputTargetPath(write.targetPath(), writePath + ".targetPath");
            if (write.conflictPolicy() == null) invalid(writePath + ".conflictPolicy 不能为空");
            validateFileOutputFormatOptions(write.formatOptions(), writePath + ".formatOptions");
        }
    }

    private static void validateWriteList(List<?> writes, String path) {
        if (writes == null) invalid(path + " 必须是数组");
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < writes.size(); index++) {
            Object write = writes.get(index);
            if (write == null) invalid(path + "[" + index + "] 不能为空");
            String writeId = switch (write) {
                case JdbcOutputWrite value -> value.writeId();
                case ModelOutputWrite value -> value.writeId();
                case KafkaOutputWrite value -> value.writeId();
                case FileOutputWrite value -> value.writeId();
                default -> throw new IllegalStateException("Unexpected output write type");
            };
            requireUuid(writeId, path + "[" + index + "].writeId");
            if (!ids.add(writeId)) invalid(path + " 中 writeId 重复：" + writeId);
        }
    }

    private static void validateFileOutputFormatOptions(FileOutputFormatOptions formatOptions, String path) {
        if (formatOptions == null) invalid(path + " 不能为空");
        switch (formatOptions) {
            case FileOutputFormatOptions.Csv csv -> {
                requireSingleCharacter(csv.delimiter(), path + ".delimiter");
                requireSingleCharacter(csv.quote(), path + ".quote");
                requireSingleCharacter(csv.escape(), path + ".escape");
                requireString(csv.nullValue(), path + ".nullValue");
            }
            case FileOutputFormatOptions.JsonLines ignored -> { }
            case FileOutputFormatOptions.Parquet ignored -> { }
            case FileOutputFormatOptions.Shapefile ignored -> { }
            case FileOutputFormatOptions.GeoParquet geoParquet -> {
                requireString(geoParquet.geometryColumnName(), path + ".geometryColumnName");
                if (geoParquet.compression() == null) invalid(path + ".compression 不能为空");
                if (geoParquet.coveringMode() == null) invalid(path + ".coveringMode 不能为空");
            }
            case FileOutputFormatOptions.GeoJson geoJson -> {
                requireString(geoJson.baseName(), path + ".baseName");
                requireString(geoJson.geometryColumnName(), path + ".geometryColumnName");
                if (geoJson.idColumnName() != null && geoJson.idColumnName().isBlank()) {
                    invalid(path + ".idColumnName 必须为 null 或非空字符串");
                }
            }
            case null -> invalid(path + " 不能为空");
        }
    }

    private static void validateJdbcReadOptionsVersion(CanvasDefinition definition) {
        if (definition.effectiveSchemaMinorVersion() >= 1 || definition.nodes() == null) return;
        for (int index = 0; index < definition.nodes().size(); index++) {
            CanvasNodeDefinition node = definition.nodes().get(index);
            if (!(node instanceof JdbcInputNodeDefinition input)
                    || input.configuration() == null || input.configuration().tables() == null) {
                continue;
            }
            boolean configured = input.configuration().tables().stream()
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(table -> table.readOptions() != null && !table.readOptions().isEmpty());
            if (configured) {
                invalid("nodes[" + index + "].configuration.tables.readOptions 从 Canvas 3.1 开始支持");
            }
        }
    }

    private static void validateTriggerInterval(Integer value, String path) {
        if (value == null || value < 1 || value > 300) {
            invalid(path + " 必须在 1 到 300 之间");
        }
    }

    private static void validateGeometryConstruct(
            GeometryConstructConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        requireString(configuration.outputColumnName(), path + ".outputColumnName");
        if (configuration.source() == null) invalid(path + ".source 不能为空");
        switch (configuration.source()) {
            case GeometryConstructSource.Wkt source ->
                    requireString(source.columnName(), path + ".source.columnName");
            case GeometryConstructSource.Wkb source ->
                    requireString(source.columnName(), path + ".source.columnName");
            case GeometryConstructSource.GeoJson source ->
                    requireString(source.columnName(), path + ".source.columnName");
            case GeometryConstructSource.PointFromXy source -> {
                requireString(source.xColumnName(), path + ".source.xColumnName");
                requireString(source.yColumnName(), path + ".source.yColumnName");
            }
        }
    }

    private static void validateGeometryValidate(
            GeometryValidateConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        requireString(configuration.geometryColumnName(), path + ".geometryColumnName");
        requireString(configuration.validColumnName(), path + ".validColumnName");
        if (configuration.reasonColumnName() != null) {
            requireString(configuration.reasonColumnName(), path + ".reasonColumnName");
        }
    }

    private static void validateSpatialMeasure(
            SpatialMeasureConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        if (configuration.measurements() == null) invalid(path + ".measurements 必须是数组");
        if (configuration.measurements().size() > SpatialMeasureConfiguration.MAX_MEASUREMENTS) {
            invalid(path + ".measurements 不能超过 "
                    + SpatialMeasureConfiguration.MAX_MEASUREMENTS + " 项");
        }
        for (int index = 0; index < configuration.measurements().size(); index++) {
            SpatialMeasurement measurement = configuration.measurements().get(index);
            String itemPath = path + ".measurements[" + index + "]";
            if (measurement == null) invalid(itemPath + " 不能为空");
            requireString(measurement.outputColumnName(), itemPath + ".outputColumnName");
            switch (measurement) {
                case SpatialMeasurement.Area item -> {
                    requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
                    requireSpatialMeasureMode(item.mode(), itemPath + ".mode");
                }
                case SpatialMeasurement.Length item -> {
                    requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
                    requireSpatialMeasureMode(item.mode(), itemPath + ".mode");
                }
                case SpatialMeasurement.Perimeter item -> {
                    requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
                    requireSpatialMeasureMode(item.mode(), itemPath + ".mode");
                }
                case SpatialMeasurement.Distance item -> {
                    requireString(item.leftGeometryColumnName(), itemPath + ".leftGeometryColumnName");
                    requireString(item.rightGeometryColumnName(), itemPath + ".rightGeometryColumnName");
                    requireSpatialMeasureMode(item.mode(), itemPath + ".mode");
                }
                case SpatialMeasurement.X item ->
                        requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
                case SpatialMeasurement.Y item ->
                        requireString(item.geometryColumnName(), itemPath + ".geometryColumnName");
            }
        }
    }

    private static void requireSpatialMeasureMode(SpatialMeasureMode mode, String path) {
        if (mode == null) invalid(path + " 不能为空");
    }

    private static void validateGeometrySerialize(
            GeometrySerializeConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        requireString(configuration.geometryColumnName(), path + ".geometryColumnName");
        requireString(configuration.outputColumnName(), path + ".outputColumnName");
        if (configuration.format() == null) invalid(path + ".format 不能为空");
    }

    private static void validateNullHandling(
            NullHandlingConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        if (configuration.rules() == null) invalid(path + ".rules 必须是数组");
        if (configuration.rules().size() > CanvasNullHandlingLimits.MAX_RULES) {
            invalid(path + ".rules 不能超过 " + CanvasNullHandlingLimits.MAX_RULES + " 项");
        }
        for (int index = 0; index < configuration.rules().size(); index++) {
            NullHandlingRule rule = configuration.rules().get(index);
            String rulePath = path + ".rules[" + index + "]";
            if (rule == null) invalid(rulePath + " 不能为空");
            switch (rule) {
                case DropNullRowsRule drop -> {
                    if (drop.columnNames() == null) {
                        invalid(rulePath + ".columnNames 必须是数组");
                    }
                    for (int columnIndex = 0;
                         columnIndex < drop.columnNames().size();
                         columnIndex++) {
                        requireString(
                                drop.columnNames().get(columnIndex),
                                rulePath + ".columnNames[" + columnIndex + "]"
                        );
                    }
                    if (drop.matchMode() == null) {
                        invalid(rulePath + ".matchMode 不能为空");
                    }
                }
                case FillNullLiteralRule fill -> {
                    requireString(fill.columnName(), rulePath + ".columnName");
                    if (fill.value() == null
                            || fill.value().dataType() == null
                            || fill.value().value() == null) {
                        invalid(rulePath + ".value 必须是非 NULL Literal");
                    }
                }
            }
        }
    }

    private static void validateValueMapping(
            ValueMappingConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        if (configuration.rules() == null) invalid(path + ".rules 必须是数组");
        if (configuration.rules().size() > CanvasValueMappingLimits.MAX_RULES) {
            invalid(path + ".rules 不能超过 " + CanvasValueMappingLimits.MAX_RULES + " 项");
        }
        int totalEntries = 0;
        for (int index = 0; index < configuration.rules().size(); index++) {
            ValueMappingRule rule = configuration.rules().get(index);
            String rulePath = path + ".rules[" + index + "]";
            if (rule == null) invalid(rulePath + " 不能为空");
            requireString(rule.columnName(), rulePath + ".columnName");
            if (rule.entries() == null) invalid(rulePath + ".entries 必须是数组");
            if (rule.entries().size() > CanvasValueMappingLimits.MAX_ENTRIES_PER_RULE) {
                invalid(rulePath + ".entries 不能超过 "
                        + CanvasValueMappingLimits.MAX_ENTRIES_PER_RULE + " 项");
            }
            totalEntries += rule.entries().size();
            for (int entryIndex = 0; entryIndex < rule.entries().size(); entryIndex++) {
                ValueMappingEntry entry = rule.entries().get(entryIndex);
                String entryPath = rulePath + ".entries[" + entryIndex + "]";
                if (entry == null
                        || entry.sourceValue() == null
                        || entry.sourceValue().dataType() == null
                        || entry.sourceValue().value() == null) {
                    invalid(entryPath + ".sourceValue 必须是非 NULL Literal");
                }
                if (entry.targetValue() != null
                        && (entry.targetValue().dataType() == null
                        || entry.targetValue().value() == null)) {
                    invalid(entryPath + ".targetValue 不完整");
                }
            }
            if (rule.unmatchedStrategy() == null) {
                invalid(rulePath + ".unmatchedStrategy 不能为空");
            }
            if (rule.unmatchedValue() != null
                    && (rule.unmatchedValue().dataType() == null
                    || rule.unmatchedValue().value() == null)) {
                invalid(rulePath + ".unmatchedValue 不完整");
            }
        }
        if (totalEntries > CanvasValueMappingLimits.MAX_TOTAL_ENTRIES) {
            invalid(path + ".rules 映射项总数不能超过 "
                    + CanvasValueMappingLimits.MAX_TOTAL_ENTRIES);
        }
    }

    private static void validateMaskFields(
            MaskFieldsConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        if (configuration.fieldRules() == null) invalid(path + ".fieldRules 必须是数组");
        if (configuration.fieldRules().size() > CanvasMaskingLimits.MAX_FIELD_RULES) {
            invalid(path + ".fieldRules 不能超过 "
                    + CanvasMaskingLimits.MAX_FIELD_RULES + " 项");
        }
        for (int index = 0; index < configuration.fieldRules().size(); index++) {
            MaskFieldRule rule = configuration.fieldRules().get(index);
            String rulePath = path + ".fieldRules[" + index + "]";
            if (rule == null) invalid(rulePath + " 不能为空");
            requireString(rule.fieldName(), rulePath + ".fieldName");
            if (rule.ruleSource() == null) {
                invalid(rulePath + ".ruleSource 不能为空");
            }
            if (rule.ruleSource() == MaskingRuleSource.GLOBAL) {
                MaskingSourceRuleReference reference = rule.sourceRuleRef();
                if (reference == null) invalid(rulePath + ".sourceRuleRef 不能为空");
                requireUuid(reference.ruleId(), rulePath + ".sourceRuleRef.ruleId");
                requireName(reference.ruleCode(), rulePath + ".sourceRuleRef.ruleCode");
                requireName(reference.ruleName(), rulePath + ".sourceRuleRef.ruleName");
            } else if (rule.sourceRuleRef() != null) {
                invalid(rulePath + ".sourceRuleRef 仅允许 GLOBAL 规则配置");
            }
            validateMaskingDefinition(rule.definition(), rulePath + ".definition");
        }
    }

    private static void validateJsonExtract(
            JsonExtractConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        requireString(configuration.sourceColumnName(), path + ".sourceColumnName");
        if (configuration.extractions() == null) {
            invalid(path + ".extractions 必须是数组");
        }
        if (configuration.extractions().size() > CanvasJsonExtractLimits.MAX_EXTRACTIONS) {
            invalid(path + ".extractions 不能超过 "
                    + CanvasJsonExtractLimits.MAX_EXTRACTIONS + " 项");
        }
        for (int index = 0; index < configuration.extractions().size(); index++) {
            JsonExtraction extraction = configuration.extractions().get(index);
            String extractionPath = path + ".extractions[" + index + "]";
            if (extraction == null) invalid(extractionPath + " 不能为空");
            requireString(extraction.jsonPath(), extractionPath + ".jsonPath");
            if (extraction.jsonPath().length() > CanvasJsonExtractLimits.MAX_JSON_PATH_LENGTH) {
                invalid(extractionPath + ".jsonPath 不能超过 "
                        + CanvasJsonExtractLimits.MAX_JSON_PATH_LENGTH + " 个字符");
            }
            requireString(
                    extraction.outputColumnName(),
                    extractionPath + ".outputColumnName"
            );
            if (extraction.targetType() == null) {
                invalid(extractionPath + ".targetType 不能为空");
            }
            if (extraction.targetType().type() == PlatformDataType.GEOMETRY) {
                invalid(extractionPath + ".targetType 不支持 GEOMETRY");
            }
        }
        if (configuration.failureStrategy() == null) {
            invalid(path + ".failureStrategy 不能为空");
        }
    }

    private static void validateMaskingDefinition(
            MaskingRuleDefinition definition,
            String path
    ) {
        if (definition == null || definition.strategy() == null) {
            invalid(path + ".strategy 不能为空");
        }
        switch (definition.strategy()) {
            case PARTIAL_MASK -> {
                validateKeepLength(
                        definition.keepPrefixLength(),
                        path + ".keepPrefixLength"
                );
                validateKeepLength(
                        definition.keepSuffixLength(),
                        path + ".keepSuffixLength"
                );
                validateMaskCharacter(definition.maskCharacter(), path + ".maskCharacter");
                if (definition.maskPosition() != null || definition.fixedValue() != null) {
                    invalid(path + " 包含不适用于 PARTIAL_MASK 的参数");
                }
            }
            case POSITION_MASK -> {
                validateMaskPosition(definition.maskPosition(), path + ".maskPosition");
                validateMaskCharacter(definition.maskCharacter(), path + ".maskCharacter");
                if (definition.keepPrefixLength() != null
                        || definition.keepSuffixLength() != null
                        || definition.fixedValue() != null) {
                    invalid(path + " 包含不适用于 POSITION_MASK 的参数");
                }
            }
            case KEEP_LENGTH_MASK -> {
                validateMaskCharacter(definition.maskCharacter(), path + ".maskCharacter");
                if (definition.keepPrefixLength() != null
                        || definition.keepSuffixLength() != null
                        || definition.maskPosition() != null
                        || definition.fixedValue() != null) {
                    invalid(path + " 包含不适用于 KEEP_LENGTH_MASK 的参数");
                }
            }
            case FIXED_VALUE -> {
                if (definition.fixedValue() == null
                        || definition.fixedValue().length()
                        > CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH) {
                    invalid(path + ".fixedValue 长度不能超过 "
                            + CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH);
                }
                if (definition.keepPrefixLength() != null
                        || definition.keepSuffixLength() != null
                        || definition.maskPosition() != null
                        || definition.maskCharacter() != null) {
                    invalid(path + " 包含不适用于 FIXED_VALUE 的参数");
                }
            }
            case NULLIFY -> {
                if (definition.keepPrefixLength() != null
                        || definition.keepSuffixLength() != null
                        || definition.maskPosition() != null
                        || definition.maskCharacter() != null
                        || definition.fixedValue() != null) {
                    invalid(path + " 包含不适用于 NULLIFY 的参数");
                }
            }
        }
    }

    private static void validateKeepLength(Integer value, String path) {
        if (value == null || value < 0 || value > CanvasMaskingLimits.MAX_KEEP_LENGTH) {
            invalid(path + " 必须在 0.." + CanvasMaskingLimits.MAX_KEEP_LENGTH + " 之间");
        }
    }

    private static void validateMaskPosition(Integer value, String path) {
        int effective = value == null ? 2 : value;
        if (effective < 1 || effective > CanvasMaskingLimits.MAX_MASK_POSITION) {
            invalid(path + " 必须在 1.." + CanvasMaskingLimits.MAX_MASK_POSITION + " 之间");
        }
    }

    private static void validateMaskCharacter(String value, String path) {
        String effective = value == null ? "*" : value;
        if (effective.codePointCount(0, effective.length()) != 1) {
            invalid(path + " 必须是一个 Unicode 字符");
        }
    }

    private static void validateWindow(
            WindowConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        validateStringArray(configuration.partitionByColumns(), path + ".partitionByColumns");
        validateSortFields(configuration.orderBy(), path + ".orderBy");
        if (configuration.functions() == null) invalid(path + ".functions 必须是数组");
        if (configuration.functions().size() > CanvasWindowLimits.MAX_FUNCTIONS) {
            invalid(path + ".functions 不能超过 " + CanvasWindowLimits.MAX_FUNCTIONS + " 项");
        }
        for (int index = 0; index < configuration.functions().size(); index++) {
            WindowFunctionItem item = configuration.functions().get(index);
            String itemPath = path + ".functions[" + index + "]";
            if (item == null) invalid(itemPath + " 不能为空");
            requireString(item.outputColumnName(), itemPath + ".outputColumnName");
            switch (item) {
                case WindowFunctionItem.RowNumber ignored -> {
                }
                case WindowFunctionItem.Rank ignored -> {
                }
                case WindowFunctionItem.DenseRank ignored -> {
                }
                case WindowFunctionItem.Lag lag -> {
                    requireString(lag.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateOptionalLiteral(lag.defaultValue(), itemPath + ".defaultValue");
                }
                case WindowFunctionItem.Lead lead -> {
                    requireString(lead.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateOptionalLiteral(lead.defaultValue(), itemPath + ".defaultValue");
                }
                case WindowFunctionItem.Count count -> {
                    if (count.sourceColumnName() != null) {
                        requireString(count.sourceColumnName(), itemPath + ".sourceColumnName");
                    }
                    validateRowsFrame(count.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.Sum sum -> {
                    requireString(sum.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(sum.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.Avg avg -> {
                    requireString(avg.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(avg.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.Min min -> {
                    requireString(min.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(min.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.Max max -> {
                    requireString(max.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(max.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.FirstValue first -> {
                    requireString(first.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(first.frame(), itemPath + ".frame");
                }
                case WindowFunctionItem.LastValue last -> {
                    requireString(last.sourceColumnName(), itemPath + ".sourceColumnName");
                    validateRowsFrame(last.frame(), itemPath + ".frame");
                }
            }
        }
    }

    private static void validateTopN(
            TopNConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireString(configuration.sourceTableName(), path + ".sourceTableName");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        validateStringArray(configuration.partitionByColumns(), path + ".partitionByColumns");
        validateSortFields(configuration.orderBy(), path + ".orderBy");
        if (configuration.tieStrategy() == null) {
            invalid(path + ".tieStrategy 不能为空");
        }
    }

    private static void validateSqlTransform(
            SqlTransformConfiguration configuration,
            String path
    ) {
        if (configuration == null) {
            invalid(path + " 不能为空");
        }
        if (configuration.sql().length() > CanvasSqlTransformLimits.MAX_SQL_LENGTH) {
            invalid(path + ".sql 不能超过 " + CanvasSqlTransformLimits.MAX_SQL_LENGTH + " 个字符");
        }
    }

    private static void validateStringArray(List<String> values, String path) {
        if (values == null) invalid(path + " 必须是数组");
        for (int index = 0; index < values.size(); index++) {
            requireString(values.get(index), path + "[" + index + "]");
        }
    }

    private static void validateSortFields(List<SortField> fields, String path) {
        if (fields == null) invalid(path + " 必须是数组");
        for (int index = 0; index < fields.size(); index++) {
            SortField field = fields.get(index);
            String itemPath = path + "[" + index + "]";
            if (field == null) invalid(itemPath + " 不能为空");
            requireString(field.columnName(), itemPath + ".columnName");
            if (field.direction() == null) invalid(itemPath + ".direction 不能为空");
            if (field.nullOrdering() == null) invalid(itemPath + ".nullOrdering 不能为空");
        }
    }

    private static void validateOptionalLiteral(CanvasLiteral literal, String path) {
        if (literal != null && (literal.dataType() == null || literal.value() == null)) {
            invalid(path + " 不完整");
        }
    }

    private static void validateRowsFrame(RowsWindowFrame frame, String path) {
        if (frame == null || frame.type() == null
                || frame.start() == null || frame.end() == null) {
            invalid(path + " 不完整");
        }
    }

    private static void validateFilterCondition(
            CanvasFilterCondition condition,
            String path,
            int depth,
            int[] conditionNodes
    ) {
        if (depth > CanvasFilterLimits.MAX_DEPTH) {
            invalid(path + " 超过最大嵌套深度 " + CanvasFilterLimits.MAX_DEPTH);
        }
        conditionNodes[0]++;
        if (conditionNodes[0] > CanvasFilterLimits.MAX_CONDITION_NODES) {
            invalid("Filter 条件节点不能超过 " + CanvasFilterLimits.MAX_CONDITION_NODES);
        }
        switch (condition) {
            case CanvasFilterGroup group -> {
                if (group.operator() == null) invalid(path + ".operator 不能为空");
                if (group.children() == null) invalid(path + ".children 必须是数组");
                for (int index = 0; index < group.children().size(); index++) {
                    CanvasFilterCondition child = group.children().get(index);
                    if (child == null) invalid(path + ".children[" + index + "] 不能为空");
                    validateFilterCondition(
                            child,
                            path + ".children[" + index + "]",
                            depth + 1,
                            conditionNodes
                    );
                }
            }
            case CanvasFieldPredicate predicate -> {
                requireString(predicate.columnName(), path + ".columnName");
                if (predicate.operator() == null) invalid(path + ".operator 不能为空");
                if (predicate.values() == null) invalid(path + ".values 必须是数组");
                if (predicate.values().size() > CanvasFilterLimits.MAX_VALUES_PER_PREDICATE) {
                    invalid(path + ".values 不能超过 " + CanvasFilterLimits.MAX_VALUES_PER_PREDICATE + " 项");
                }
                for (int index = 0; index < predicate.values().size(); index++) {
                    CanvasLiteral literal = predicate.values().get(index);
                    if (literal == null || literal.dataType() == null) {
                        invalid(path + ".values[" + index + "] 不完整");
                    }
                }
            }
        }
    }

    private static void validateCanvasExpression(
            CanvasExpression expression,
            String path,
            int depth,
            int[] expressionNodes
    ) {
        if (depth > CanvasExpressionLimits.MAX_DEPTH) {
            invalid(path + " 超过最大嵌套深度 " + CanvasExpressionLimits.MAX_DEPTH);
        }
        expressionNodes[0]++;
        if (expressionNodes[0] > CanvasExpressionLimits.MAX_EXPRESSION_NODES) {
            invalid("派生表达式节点不能超过 " + CanvasExpressionLimits.MAX_EXPRESSION_NODES);
        }
        switch (expression) {
            case ColumnExpression column ->
                    requireString(column.columnName(), path + ".columnName");
            case LiteralExpression literal -> {
                if (literal.literal() == null || literal.literal().dataType() == null) {
                    invalid(path + ".literal 不完整");
                }
            }
            case RuntimeValueExpression runtime -> {
                if (runtime.value() == null) {
                    invalid(path + ".value 必须是受支持的运行时变量");
                }
            }
            case BinaryExpression binary -> {
                if (binary.operator() == null) invalid(path + ".operator 不能为空");
                if (binary.left() == null) invalid(path + ".left 不能为空");
                if (binary.right() == null) invalid(path + ".right 不能为空");
                validateCanvasExpression(binary.left(), path + ".left", depth + 1, expressionNodes);
                validateCanvasExpression(binary.right(), path + ".right", depth + 1, expressionNodes);
            }
            case FunctionExpression function -> {
                if (function.function() == null) invalid(path + ".function 不能为空");
                if (function.arguments() == null) invalid(path + ".arguments 必须是数组");
                for (int index = 0; index < function.arguments().size(); index++) {
                    CanvasExpression argument = function.arguments().get(index);
                    if (argument == null) invalid(path + ".arguments[" + index + "] 不能为空");
                    validateCanvasExpression(
                            argument,
                            path + ".arguments[" + index + "]",
                            depth + 1,
                            expressionNodes
                    );
                }
            }
            case CaseWhenExpression caseWhen -> {
                if (caseWhen.branches() == null) invalid(path + ".branches 必须是数组");
                if (caseWhen.branches().size() > CanvasExpressionLimits.MAX_CASE_BRANCHES) {
                    invalid(path + ".branches 不能超过 "
                            + CanvasExpressionLimits.MAX_CASE_BRANCHES + " 项");
                }
                for (int index = 0; index < caseWhen.branches().size(); index++) {
                    CaseWhenBranch branch = caseWhen.branches().get(index);
                    String branchPath = path + ".branches[" + index + "]";
                    if (branch == null || branch.condition() == null || branch.result() == null) {
                        invalid(branchPath + " 不完整");
                    }
                    validateFilterCondition(
                            branch.condition(),
                            branchPath + ".condition",
                            1,
                            new int[]{0}
                    );
                    validateCanvasExpression(
                            branch.result(),
                            branchPath + ".result",
                            depth + 1,
                            expressionNodes
                    );
                }
                if (caseWhen.elseExpression() != null) {
                    validateCanvasExpression(
                            caseWhen.elseExpression(),
                            path + ".elseExpression",
                            depth + 1,
                            expressionNodes
                    );
                }
            }
        }
    }

    private static void requireSingleCharacter(String value, String path) {
        if (value == null || value.codePointCount(0, value.length()) != 1
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            invalid(path + " 必须是一个非换行字符");
        }
    }

    private static void validateFileOutputTargetPath(String value, String path) {
        requireString(value, path);
        if (value.isBlank() || value.length() > 1024
                || value.startsWith("/") || value.contains("\\")
                || value.contains("://") || value.contains("?") || value.contains("#")) {
            invalid(path + " 必须是非空的 S3 相对路径");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || "_temporary".equalsIgnoreCase(segment)) {
                invalid(path + " 不能包含空段、.、.. 或 _temporary");
            }
        }
    }

    private static void validateKafkaValueSchema(
            KafkaValueSchema schema,
            String path
    ) {
        if (schema == null || schema.columns() == null) {
            invalid(path + ".columns 必须是数组");
        }
        for (int index = 0; index < schema.columns().size(); index++) {
            KafkaValueColumn column = schema.columns().get(index);
            if (column == null || column.fieldType() == null) {
                invalid(path + ".columns[" + index + "] 不完整");
            }
            if (column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                invalid(path + ".columns[" + index + "] 不支持空间字段");
            }
            requireString(column.name(), path + ".columns[" + index + "].name");
        }
    }

    private static void validateKafkaInputConfiguration(
            KafkaInputConfiguration configuration,
            String path
    ) {
        if (configuration == null) invalid(path + " 不能为空");
        requireOptionalUuid(configuration.dataSourceId(), path + ".dataSourceId");
        requireString(configuration.topic(), path + ".topic");
        requireString(configuration.outputTableName(), path + ".outputTableName");
        validateTriggerInterval(configuration.triggerIntervalSeconds(), path + ".triggerIntervalSeconds");

        KafkaInputValueFormat valueFormat = configuration.effectiveValueFormat();
        KafkaValueSchema valueSchema = configuration.valueSchema();
        if (valueFormat == KafkaInputValueFormat.JSON) {
            validateKafkaValueSchema(valueSchema, path + ".valueSchema");
        } else if (valueSchema == null || valueSchema.columns() == null) {
            invalid(path + ".valueSchema.columns 必须是数组");
        } else if (!valueSchema.columns().isEmpty()) {
            invalid(path + ".valueSchema.columns 在 TEXT/BINARY 格式下必须为空");
        }

        Set<KafkaInputMetadataField> metadataFields = new HashSet<>();
        List<KafkaInputMetadataField> configuredMetadataFields = configuration.effectiveMetadataFields();
        for (int index = 0; index < configuredMetadataFields.size(); index++) {
            KafkaInputMetadataField metadataField = configuredMetadataFields.get(index);
            if (metadataField == null) {
                invalid(path + ".metadataFields[" + index + "] 不能为空");
            }
            if (!metadataFields.add(metadataField)) {
                invalid(path + ".metadataFields[" + index + "] 重复配置：" + metadataField);
            }
        }

        if (valueFormat == KafkaInputValueFormat.JSON && valueSchema != null) {
            Set<String> metadataColumnNames = metadataFields.stream()
                    .map(CanvasDefinitionValidator::kafkaMetadataColumnName)
                    .collect(java.util.stream.Collectors.toSet());
            for (int index = 0; index < valueSchema.columns().size(); index++) {
                KafkaValueColumn column = valueSchema.columns().get(index);
                if (column != null && metadataColumnNames.contains(column.name())) {
                    invalid(path + ".valueSchema.columns[" + index + "].name 与 Kafka 元数据字段重名："
                            + column.name());
                }
            }
        }
    }

    private static String kafkaMetadataColumnName(KafkaInputMetadataField field) {
        return switch (field) {
            case KEY -> "_kafka_key";
            case TOPIC -> "_kafka_topic";
            case PARTITION -> "_kafka_partition";
            case OFFSET -> "_kafka_offset";
            case TIMESTAMP -> "_kafka_timestamp";
        };
    }

    private static void validateJoinConditions(List<JoinCondition> conditions, String path) {
        if (conditions == null) invalid(path + " 必须是数组");
        for (int index = 0; index < conditions.size(); index++) {
            JoinCondition condition = conditions.get(index);
            if (condition == null || condition.operator() == null) {
                invalid(path + "[" + index + "] 不完整");
            }
            requireString(condition.leftColumnName(), path + "[" + index + "].leftColumnName");
            requireString(condition.rightColumnName(), path + "[" + index + "].rightColumnName");
        }
    }

    private static void validateMappings(
            List<JdbcColumnMapping> mappings,
            String path
    ) {
        if (mappings == null) invalid(path + " 必须是数组");
        for (int index = 0; index < mappings.size(); index++) {
            JdbcColumnMapping mapping = mappings.get(index);
            if (mapping == null) invalid(path + "[" + index + "] 不能为空");
            requireString(mapping.sourceColumnName(), path + "[" + index + "].sourceColumnName");
            requireString(mapping.targetColumnName(), path + "[" + index + "].targetColumnName");
        }
    }

    private static void validateSnapshotSyncConfiguration(
            SnapshotSyncConfiguration configuration,
            String path
    ) {
        validateStringArray(configuration.keyColumns(), path + ".keyColumns");
        if (configuration.keyColumns().size() > 32) {
            invalid(path + ".keyColumns 不能超过 32 项");
        }
        validateMappings(configuration.columnMappings(), path + ".columnMappings");
        if (configuration.deletePolicy() == null) {
            invalid(path + ".deletePolicy 不能为空");
        }
    }

    private static void validateLayout(CanvasNodeLayout layout, String path) {
        if (layout == null
                || !Double.isFinite(layout.x()) || !Double.isFinite(layout.y())
                || !Double.isFinite(layout.width()) || !Double.isFinite(layout.height())) {
            invalid(path + " 必须包含有限数值 x、y、width、height");
        }
        if (layout.x() < -100_000 || layout.x() > 100_000
                || layout.y() < -100_000 || layout.y() > 100_000
                || layout.width() < 180 || layout.width() > 1_000
                || layout.height() < 96 || layout.height() > 1_000) {
            invalid(path + " 超出允许范围");
        }
    }

    private static void requireName(String value, String path) {
        if (value == null || value.isBlank() || value.length() > 100) {
            invalid(path + " 长度必须为 1 到 100 个字符");
        }
    }

    private static void requireString(String value, String path) {
        if (value == null) {
            invalid(path + " 必须是字符串");
        }
    }

    private static void validateProcessorOperations(
            List<? extends ProcessorOperation> operations,
            String path
    ) {
        if (operations == null) {
            invalid(path + ".operations 必须是数组");
        }
        for (int index = 0; index < operations.size(); index++) {
            ProcessorOperation operation = operations.get(index);
            String operationPath = path + ".operations[" + index + "]";
            if (operation == null) invalid(operationPath + " 不能为空");
            requireUuid(operation.operationId(), operationPath + ".operationId");
            requireString(operation.sourceTableName(), operationPath + ".sourceTableName");
            if (operation.output() == null) invalid(operationPath + ".output 不能为空");
            if (operation.output().outputTableName() != null) {
                requireString(operation.output().outputTableName(), operationPath + ".output.outputTableName");
            }
        }
    }

    private static void validateTypeCastConfiguration(
            TypeCastConfiguration configuration,
            String path
    ) {
        if (configuration == null) {
            invalid(path + " 不能为空");
        }
        validateProcessorOperations(configuration.operations(), path);
        for (int operationIndex = 0; operationIndex < configuration.operations().size(); operationIndex++) {
            TypeCastOperation operation = configuration.operations().get(operationIndex);
            if (operation == null || operation.casts() == null) continue;
            for (int castIndex = 0; castIndex < operation.casts().size(); castIndex++) {
                ColumnTypeCast cast = operation.casts().get(castIndex);
                if (cast == null) continue;
                String castPath = path + ".operations[" + operationIndex + "].casts[" + castIndex + "]";
                if (cast.epochTimestampUnit() != null
                        && (cast.targetType() == null
                        || cast.targetType().type() != PlatformDataType.TIMESTAMP
                        && cast.targetType().type() != PlatformDataType.LONG)) {
                    invalid(path + ".operations[" + operationIndex + "].casts[" + castIndex
                            + "].epochTimestampUnit 仅支持 LONG 转 TIMESTAMP，或 DATE/TIMESTAMP 转 LONG");
                }
                validateStringTemporalParseOptions(cast, castPath);
                validateTemporalStringFormatOptions(cast, castPath);
            }
        }
    }

    private static void validateStringTemporalParseOptions(ColumnTypeCast cast, String castPath) {
        StringTemporalParseOptions options = cast.stringTemporalParseOptions();
        if (options == null) return;
        String optionsPath = castPath + ".stringTemporalParseOptions";
        if (cast.epochTimestampUnit() != null || cast.temporalStringFormatOptions() != null) {
            invalid(optionsPath + " 不能与其他特殊时间转换配置同时使用");
        }
        if (cast.targetType() == null || (cast.targetType().type() != PlatformDataType.DATE
                && cast.targetType().type() != PlatformDataType.TIMESTAMP)) {
            invalid(optionsPath + " 仅支持 STRING 转换为 DATE 或 TIMESTAMP");
        }
        if (options.pattern() == null || options.pattern().isBlank()) {
            invalid(optionsPath + ".pattern 不能为空");
        }
        if (options.pattern() != null && options.pattern().length() > 128) {
            invalid(optionsPath + ".pattern 不能超过 128 个字符");
        }
        if (cast.targetType().type() == PlatformDataType.DATE) {
            if (options.zoneMode() != null || options.sourceTimeZone() != null) {
                invalid(optionsPath + " 的 DATE 解析不能配置时区");
            }
            return;
        }
        if (options.zoneMode() == null) {
            invalid(optionsPath + ".zoneMode 不能为空");
        }
        if (options.sourceTimeZone() != null && options.sourceTimeZone().length() > 64) {
            invalid(optionsPath + ".sourceTimeZone 不能超过 64 个字符");
        }
        if (options.zoneMode() == StringTimestampZoneMode.SOURCE_TIME_ZONE) {
            if (options.sourceTimeZone() == null || options.sourceTimeZone().isBlank()) {
                invalid(optionsPath + ".sourceTimeZone 不能为空");
            }
            try {
                ZoneId.of(options.sourceTimeZone());
            } catch (DateTimeException exception) {
                invalid(optionsPath + ".sourceTimeZone 必须是有效的 IANA Zone ID");
            }
            if (containsUnquotedZonePatternSymbol(options.pattern())) {
                invalid(optionsPath + ".pattern 在指定来源时区模式下不能包含时区或偏移符号");
            }
        } else if (options.zoneMode() == StringTimestampZoneMode.EMBEDDED_OFFSET
                && options.sourceTimeZone() != null) {
            invalid(optionsPath + ".sourceTimeZone 在字符串自带偏移模式下必须为空");
        } else if (options.zoneMode() == StringTimestampZoneMode.EMBEDDED_OFFSET
                && !containsUnquotedZonePatternSymbol(options.pattern())) {
            invalid(optionsPath + ".pattern 在字符串自带偏移模式下必须包含时区或偏移符号");
        }
    }

    private static void validateTemporalStringFormatOptions(ColumnTypeCast cast, String castPath) {
        TemporalStringFormatOptions options = cast.temporalStringFormatOptions();
        if (options == null) return;
        String optionsPath = castPath + ".temporalStringFormatOptions";
        if (cast.epochTimestampUnit() != null || cast.stringTemporalParseOptions() != null) {
            invalid(optionsPath + " 不能与其他特殊时间转换配置同时使用");
        }
        if (cast.targetType() == null || cast.targetType().type() != PlatformDataType.STRING) {
            invalid(optionsPath + " 仅支持 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 转换为 STRING");
        }
        if (options.pattern() == null || options.pattern().isBlank()) {
            invalid(optionsPath + ".pattern 不能为空");
        }
        if (options.pattern() != null && options.pattern().length() > 128) {
            invalid(optionsPath + ".pattern 不能超过 128 个字符");
        }
        if (options.pattern() != null && !options.pattern().isBlank()) {
            try {
                DateTimeFormatter.ofPattern(options.pattern());
            } catch (IllegalArgumentException exception) {
                invalid(optionsPath + ".pattern 不是有效的日期时间 pattern");
            }
            if (containsUnquotedZonePatternSymbol(options.pattern())) {
                invalid(optionsPath + ".pattern 不能包含时区或偏移符号");
            }
        }
        if (options.targetTimeZone() != null) {
            if (options.targetTimeZone().isBlank()) {
                invalid(optionsPath + ".targetTimeZone 不能为空字符串");
            }
            if (options.targetTimeZone().length() > 64) {
                invalid(optionsPath + ".targetTimeZone 不能超过 64 个字符");
            }
            try {
                ZoneId.of(options.targetTimeZone());
            } catch (DateTimeException exception) {
                invalid(optionsPath + ".targetTimeZone 必须是有效的 IANA Zone ID");
            }
        }
    }

    private static boolean containsUnquotedZonePatternSymbol(String pattern) {
        if (pattern == null) return false;
        boolean quoted = false;
        for (int index = 0; index < pattern.length(); index++) {
            char symbol = pattern.charAt(index);
            if (symbol == '\'') {
                if (quoted && index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && "XxZOVz".indexOf(symbol) >= 0) return true;
        }
        return false;
    }

    private static void validateFilterConfiguration(FilterConfiguration configuration, String path) {
        if (configuration == null) {
            invalid(path + " 不能为空");
        }
        validateProcessorOperations(configuration.operations(), path);
        for (int index = 0; index < configuration.operations().size(); index++) {
            FilterOperation operation = configuration.operations().get(index);
            if (operation == null) continue;
            String operationPath = path + ".operations[" + index + "]";
            if (operation.mode() == FilterConditionMode.STRUCTURED) {
                if (operation.condition() == null) {
                    invalid(operationPath + ".condition 不能为空");
                }
                validateFilterCondition(operation.condition(), operationPath + ".condition", 1, new int[]{0});
                continue;
            }
            FilterSqlExpressionPolicy.Violation violation =
                    FilterSqlExpressionPolicy.findViolation(operation.sqlExpression());
            if (violation == FilterSqlExpressionPolicy.Violation.TOO_LONG) {
                invalid(operationPath + ".sqlExpression 不能超过 "
                        + FilterSqlExpressionPolicy.MAX_EXPRESSION_LENGTH + " 个字符");
            }
            if (violation != null && violation != FilterSqlExpressionPolicy.Violation.REQUIRED) {
                invalid(operationPath + ".sqlExpression 只能包含单个布尔谓词，不能包含 WHERE、完整 SQL、注释或分号");
            }
        }
    }

    private static void validateDeriveColumnsConfiguration(
            DeriveColumnsConfiguration configuration,
            String path
    ) {
        if (configuration == null) {
            invalid(path + " 不能为空");
        }
        validateDerivations(configuration.globalDerivations(), path + ".globalDerivations");
        validateProcessorOperations(configuration.operations(), path);
        for (int index = 0; index < configuration.operations().size(); index++) {
            DeriveColumnsOperation operation = configuration.operations().get(index);
            if (operation == null) continue;
            validateDerivations(
                    operation.derivations(),
                    path + ".operations[" + index + "].derivations"
            );
        }
    }

    private static void validateDerivations(List<ColumnDerivation> derivations, String path) {
        if (derivations == null) {
            invalid(path + " 必须是数组");
        }
        if (derivations.size() > CanvasExpressionLimits.MAX_DERIVATIONS) {
            invalid(path + " 不能超过 " + CanvasExpressionLimits.MAX_DERIVATIONS + " 项");
        }
        int[] expressionNodes = {0};
        for (int index = 0; index < derivations.size(); index++) {
            ColumnDerivation derivation = derivations.get(index);
            String derivationPath = path + "[" + index + "]";
            if (derivation == null) {
                invalid(derivationPath + " 不能为空");
            }
            requireString(derivation.targetColumnName(), derivationPath + ".targetColumnName");
            if (derivation.expression() == null) {
                invalid(derivationPath + ".expression 不能为空");
            }
            validateCanvasExpression(
                    derivation.expression(), derivationPath + ".expression", 1, expressionNodes
            );
        }
    }

    private static void requireOptionalUuid(String value, String path) {
        requireString(value, path);
        if (!value.isBlank()) {
            requireUuid(value, path);
        }
    }

    private static void requireUuid(String value, String path) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            invalid(path + " 必须是 UUID");
        }
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
