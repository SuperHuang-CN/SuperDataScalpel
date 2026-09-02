package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates Canvas protocol compatibility and normalizes supported definitions to the current writer version. */
@Component
public class CanvasDefinitionUpgrader {

    public boolean supports(int schemaVersion, int schemaMinorVersion) {
        return schemaVersion == CanvasDefinition.CURRENT_SCHEMA_VERSION
                && schemaMinorVersion >= CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                && schemaMinorVersion <= CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION;
    }

    public void requireSupportedSource(CanvasDefinition definition) {
        if (definition.schemaVersion() == null
                || definition.schemaVersion() != CanvasDefinition.CURRENT_SCHEMA_VERSION) {
            invalid("Canvas schemaVersion 仅支持 " + CanvasDefinition.CURRENT_SCHEMA_VERSION);
        }
        int schemaMinorVersion = definition.effectiveSchemaMinorVersion();
        if (!supports(definition.schemaVersion(), schemaMinorVersion)) {
            invalid("Canvas schemaMinorVersion 仅支持 "
                    + CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION + " 到 "
                    + CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
        }
        if (definition.nodes() != null) {
            definition.nodes().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(node -> node.nodeType().introducedInMinorVersion() > schemaMinorVersion)
                    .findFirst()
                    .ifPresent(node -> invalid(node.nodeType() + " 从 Canvas "
                            + CanvasDefinition.CURRENT_SCHEMA_VERSION + "."
                            + node.nodeType().introducedInMinorVersion() + " 开始支持"));
        }
        requireMinorVersionFeatures(definition, schemaMinorVersion);
    }

    public CanvasDefinition upgradeToCurrent(CanvasDefinition definition) {
        return upgradeToCurrent(definition, KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS);
    }

    public CanvasDefinition upgradeToCurrent(
            CanvasDefinition definition,
            int legacyTriggerIntervalSeconds
    ) {
        requireSupportedSource(definition);
        int normalizedLegacyInterval = legacyTriggerIntervalSeconds < 1 || legacyTriggerIntervalSeconds > 300
                ? KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS
                : legacyTriggerIntervalSeconds;
        java.util.List<CanvasNodeDefinition> nodes = definition.nodes().stream()
                .map(node -> normalizeNode(node, normalizedLegacyInterval))
                .toList();
        if (definition.schemaVersion() == CanvasDefinition.CURRENT_SCHEMA_VERSION
                && definition.effectiveSchemaMinorVersion() == CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION
                && nodes.equals(definition.nodes())) {
            return definition;
        }
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                nodes,
                definition.edges()
        );
    }

    private static CanvasNodeDefinition normalizeNode(
            CanvasNodeDefinition node,
            int legacyTriggerIntervalSeconds
    ) {
        if (node instanceof KafkaInputNodeDefinition input && input.configuration() != null) {
            KafkaInputConfiguration configuration = input.configuration();
            Integer triggerIntervalSeconds = configuration.triggerIntervalSeconds() == null
                    ? legacyTriggerIntervalSeconds : configuration.triggerIntervalSeconds();
            KafkaInputValueFormat valueFormat = configuration.effectiveValueFormat();
            java.util.List<cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField> metadataFields =
                    configuration.effectiveMetadataFields();
            if (configuration.triggerIntervalSeconds() != null
                    && configuration.valueFormat() != null
                    && configuration.metadataFields() != null) {
                return node;
            }
            return new KafkaInputNodeDefinition(input.id(), input.name(), input.layout(),
                    new KafkaInputConfiguration(
                            configuration.dataSourceId(), configuration.topic(), configuration.valueSchema(),
                            configuration.outputTableName(), configuration.startingOffsets(),
                            triggerIntervalSeconds, valueFormat, metadataFields));
        }
        if (node instanceof TdEngineTmqInputNodeDefinition input
                && input.configuration() != null
                && input.configuration().triggerIntervalSeconds() == null) {
            TdEngineTmqInputConfiguration configuration = input.configuration();
            return new TdEngineTmqInputNodeDefinition(input.id(), input.name(), input.layout(),
                    new TdEngineTmqInputConfiguration(
                            configuration.dataSourceId(), configuration.topicName(),
                            configuration.catalogName(), configuration.supertableName(),
                            configuration.topicDefinitionFingerprint(), configuration.outputTableName(),
                            configuration.startingOffsets(), configuration.maxOffsetsPerVGroupPerTrigger(),
                            legacyTriggerIntervalSeconds, configuration.eventTimeColumn(),
                            configuration.watermarkDelaySeconds()));
        }
        return node;
    }

    private static void requireMinorVersionFeatures(CanvasDefinition definition, int schemaMinorVersion) {
        if (definition.nodes() == null) return;
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            CanvasNodeDefinition node = definition.nodes().get(nodeIndex);
            if (node instanceof TdEngineTmqInputNodeDefinition input
                    && input.configuration() != null
                    && schemaMinorVersion < 5
                    && (input.configuration().eventTimeColumn() != null
                    || input.configuration().watermarkDelaySeconds() != null)) {
                invalid("nodes[" + nodeIndex
                        + "].configuration.eventTimeColumn/watermarkDelaySeconds 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".5 开始支持");
            }
            if (node instanceof KafkaInputNodeDefinition input
                    && input.configuration() != null
                    && schemaMinorVersion < 4
                    && (input.configuration().valueFormat() != null
                    || input.configuration().metadataFields() != null)) {
                invalid("nodes[" + nodeIndex + "].configuration.valueFormat/metadataFields 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".4 开始支持");
            }
            if (node instanceof KafkaOutputNodeDefinition output
                    && output.configuration() != null
                    && schemaMinorVersion < 6) {
                for (int writeIndex = 0;
                     writeIndex < output.configuration().writes().size();
                     writeIndex++) {
                    var write = output.configuration().writes().get(writeIndex);
                    if (write != null && (write.valueFormat() != null
                            || write.valueColumnNames() != null && !write.valueColumnNames().isEmpty())) {
                        invalid("nodes[" + nodeIndex + "].configuration.writes[" + writeIndex
                                + "].valueFormat/valueColumnNames 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".6 开始支持");
                    }
                }
            }
            if (!(node instanceof TypeCastNodeDefinition typeCast)
                    || typeCast.configuration() == null
                    || typeCast.configuration().operations() == null) continue;
            for (int operationIndex = 0;
                 operationIndex < typeCast.configuration().operations().size();
                 operationIndex++) {
                TypeCastOperation operation = typeCast.configuration().operations().get(operationIndex);
                if (operation == null || operation.casts() == null) continue;
                for (int castIndex = 0; castIndex < operation.casts().size(); castIndex++) {
                    ColumnTypeCast cast = operation.casts().get(castIndex);
                    if (cast != null && cast.epochTimestampUnit() != null) {
                        if (schemaMinorVersion < 2) {
                            invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                    + "].casts[" + castIndex + "].epochTimestampUnit 从 Canvas "
                                    + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".2 开始支持");
                        }
                        if (schemaMinorVersion < 8
                                && cast.targetType() != null
                                && cast.targetType().type()
                                == cn.superhuang.data.scalpel.contract.type.PlatformDataType.LONG) {
                            invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                    + "].casts[" + castIndex + "] 的 DATE/TIMESTAMP 转 LONG Epoch 单位从 Canvas "
                                    + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".8 开始支持");
                        }
                    }
                    if (cast != null && schemaMinorVersion < 3 && cast.stringTemporalParseOptions() != null) {
                        invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                + "].casts[" + castIndex + "].stringTemporalParseOptions 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".3 开始支持");
                    }
                    if (cast != null && schemaMinorVersion < 7 && cast.temporalStringFormatOptions() != null) {
                        invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                + "].casts[" + castIndex + "].temporalStringFormatOptions 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".7 开始支持");
                    }
                }
            }
        }
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
