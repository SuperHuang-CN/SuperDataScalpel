package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates only the stable structure needed to persist and safely reload an incomplete Canvas draft. */
@Component
public class CanvasDefinitionValidator {

    private static final Pattern SENSITIVE_RUNTIME_PARAMETER = Pattern.compile(
            "(?i).*(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature).*"
    );

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
            CanvasDefinition.CanvasNodeDefinition node = definition.nodes().get(index);
            if (node == null) {
                invalid("nodes[" + index + "] 不能为空");
            }
            String path = "nodes[" + index + "]";
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
            CanvasDefinition.CanvasEdgeDefinition edge = definition.edges().get(index);
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

    private static void validateConfiguration(CanvasDefinition.CanvasNodeDefinition node, String path) {
        switch (node) {
            case CanvasDefinition.ModelInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().modelId(), path + ".modelId");
            }
            case CanvasDefinition.JdbcInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireString(input.configuration().tableName(), path + ".tableName");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
            }
            case CanvasDefinition.FileDatasetInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(
                        input.configuration().fileDatasetTableId(),
                        path + ".fileDatasetTableId"
                );
            }
            case CanvasDefinition.HttpApiInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
                requireOptionalUuid(input.configuration().resourceId(), path + ".resourceId");
                requireString(input.configuration().outputTableName(), path + ".outputTableName");
                if (input.configuration().runtimeParameters() == null) {
                    invalid(path + ".runtimeParameters 必须是数组");
                }
                Set<String> names = new HashSet<>();
                for (int index = 0; index < input.configuration().runtimeParameters().size(); index++) {
                    var parameter = input.configuration().runtimeParameters().get(index);
                    if (parameter == null || parameter.name() == null || parameter.value() == null
                            || !parameter.name().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")
                            || SENSITIVE_RUNTIME_PARAMETER.matcher(parameter.name()).matches()
                            || !names.add(parameter.name())) {
                        invalid(path + ".runtimeParameters[" + index + "] 无效或名称重复");
                    }
                }
            }
            case CanvasDefinition.KafkaInputNodeDefinition input -> {
                if (input.configuration() == null) invalid(path + " 不能为空");
                requireOptionalUuid(input.configuration().dataSourceId(), path + ".dataSourceId");
                requireString(input.configuration().topic(), path + ".topic");
                validateKafkaValueSchema(input.configuration().valueSchema(), path + ".valueSchema");
                requireString(input.configuration().outputTableName(), path + ".outputTableName");
            }
            case CanvasDefinition.JoinNodeDefinition join -> {
                if (join.configuration() == null) invalid(path + " 不能为空");
                requireString(join.configuration().leftTableName(), path + ".leftTableName");
                requireString(join.configuration().rightTableName(), path + ".rightTableName");
                requireString(join.configuration().outputTableName(), path + ".outputTableName");
                List<CanvasDefinition.JoinCondition> conditions = join.configuration().conditions();
                if (conditions == null) invalid(path + ".conditions 必须是数组");
                for (int index = 0; index < conditions.size(); index++) {
                    CanvasDefinition.JoinCondition condition = conditions.get(index);
                    if (condition == null || condition.operator() == null) {
                        invalid(path + ".conditions[" + index + "] 不完整");
                    }
                    requireString(condition.leftColumnName(), path + ".conditions[" + index + "].leftColumnName");
                    requireString(condition.rightColumnName(), path + ".conditions[" + index + "].rightColumnName");
                }
            }
            case CanvasDefinition.StreamJoinNodeDefinition join -> {
                if (join.configuration() == null) invalid(path + " 不能为空");
                requireString(join.configuration().leftTableName(), path + ".leftTableName");
                requireString(join.configuration().rightTableName(), path + ".rightTableName");
                requireString(join.configuration().outputTableName(), path + ".outputTableName");
                validateJoinConditions(join.configuration().conditions(), path + ".conditions");
            }
            case CanvasDefinition.RenameNodeDefinition rename -> {
                if (rename.configuration() == null) invalid(path + " 不能为空");
                requireString(rename.configuration().sourceTableName(), path + ".sourceTableName");
                requireString(rename.configuration().outputTableName(), path + ".outputTableName");
                List<CanvasDefinition.RenameColumnMapping> mappings = rename.configuration().columnMappings();
                if (mappings == null) invalid(path + ".columnMappings 必须是数组");
                for (int index = 0; index < mappings.size(); index++) {
                    CanvasDefinition.RenameColumnMapping mapping = mappings.get(index);
                    if (mapping == null) invalid(path + ".columnMappings[" + index + "] 不能为空");
                    requireString(mapping.sourceColumnName(),
                            path + ".columnMappings[" + index + "].sourceColumnName");
                    requireString(mapping.targetColumnName(),
                            path + ".columnMappings[" + index + "].targetColumnName");
                }
            }
            case CanvasDefinition.JdbcOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireString(output.configuration().sourceTableName(), path + ".sourceTableName");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                requireString(output.configuration().targetTableName(), path + ".targetTableName");
                List<CanvasDefinition.JdbcColumnMapping> mappings = output.configuration().columnMappings();
                if (mappings == null) invalid(path + ".columnMappings 必须是数组");
                for (int index = 0; index < mappings.size(); index++) {
                    CanvasDefinition.JdbcColumnMapping mapping = mappings.get(index);
                    if (mapping == null) invalid(path + ".columnMappings[" + index + "] 不能为空");
                    requireString(mapping.sourceColumnName(), path + ".columnMappings[" + index + "].sourceColumnName");
                    requireString(mapping.targetColumnName(), path + ".columnMappings[" + index + "].targetColumnName");
                }
            }
            case CanvasDefinition.ModelOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireString(output.configuration().sourceTableName(), path + ".sourceTableName");
                requireOptionalUuid(output.configuration().targetModelId(), path + ".targetModelId");
                validateMappings(output.configuration().columnMappings(), path + ".columnMappings");
            }
            case CanvasDefinition.KafkaOutputNodeDefinition output -> {
                if (output.configuration() == null) invalid(path + " 不能为空");
                requireString(output.configuration().sourceTableName(), path + ".sourceTableName");
                requireOptionalUuid(output.configuration().dataSourceId(), path + ".dataSourceId");
                requireString(output.configuration().topic(), path + ".topic");
                validateKafkaValueSchema(output.configuration().valueSchema(), path + ".valueSchema");
                requireString(output.configuration().keyColumnName(), path + ".keyColumnName");
                validateMappings(output.configuration().columnMappings(), path + ".columnMappings");
            }
        }
    }

    private static void validateKafkaValueSchema(
            CanvasDefinition.KafkaValueSchema schema,
            String path
    ) {
        if (schema == null || schema.columns() == null) {
            invalid(path + ".columns 必须是数组");
        }
        for (int index = 0; index < schema.columns().size(); index++) {
            CanvasDefinition.KafkaValueColumn column = schema.columns().get(index);
            if (column == null || column.fieldType() == null) {
                invalid(path + ".columns[" + index + "] 不完整");
            }
            if (column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                invalid(path + ".columns[" + index + "] 不支持空间字段");
            }
            requireString(column.name(), path + ".columns[" + index + "].name");
        }
    }

    private static void validateJoinConditions(List<CanvasDefinition.JoinCondition> conditions, String path) {
        if (conditions == null) invalid(path + " 必须是数组");
        for (int index = 0; index < conditions.size(); index++) {
            CanvasDefinition.JoinCondition condition = conditions.get(index);
            if (condition == null || condition.operator() == null) {
                invalid(path + "[" + index + "] 不完整");
            }
            requireString(condition.leftColumnName(), path + "[" + index + "].leftColumnName");
            requireString(condition.rightColumnName(), path + "[" + index + "].rightColumnName");
        }
    }

    private static void validateMappings(
            List<CanvasDefinition.JdbcColumnMapping> mappings,
            String path
    ) {
        if (mappings == null) invalid(path + " 必须是数组");
        for (int index = 0; index < mappings.size(); index++) {
            CanvasDefinition.JdbcColumnMapping mapping = mappings.get(index);
            if (mapping == null) invalid(path + "[" + index + "] 不能为空");
            requireString(mapping.sourceColumnName(), path + "[" + index + "].sourceColumnName");
            requireString(mapping.targetColumnName(), path + "[" + index + "].targetColumnName");
        }
    }

    private static void validateLayout(CanvasDefinition.CanvasNodeLayout layout, String path) {
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
