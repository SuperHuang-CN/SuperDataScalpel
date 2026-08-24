package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputConfiguration;
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
                .map(node -> normalizeNodeInterval(node, normalizedLegacyInterval))
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

    private static CanvasNodeDefinition normalizeNodeInterval(
            CanvasNodeDefinition node,
            int legacyTriggerIntervalSeconds
    ) {
        if (node instanceof KafkaInputNodeDefinition input
                && input.configuration() != null
                && input.configuration().triggerIntervalSeconds() == null) {
            KafkaInputConfiguration configuration = input.configuration();
            return new KafkaInputNodeDefinition(input.id(), input.name(), input.layout(),
                    new KafkaInputConfiguration(
                            configuration.dataSourceId(), configuration.topic(), configuration.valueSchema(),
                            configuration.outputTableName(), configuration.startingOffsets(),
                            legacyTriggerIntervalSeconds));
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
                            legacyTriggerIntervalSeconds));
        }
        return node;
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
