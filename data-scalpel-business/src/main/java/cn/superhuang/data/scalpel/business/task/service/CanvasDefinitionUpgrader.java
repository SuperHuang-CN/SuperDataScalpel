package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates Canvas protocol compatibility and normalizes supported definitions to the current writer version. */
@Component
public class CanvasDefinitionUpgrader {

    public void requireSupportedSource(CanvasDefinition definition) {
        if (definition.schemaVersion() != CanvasDefinition.CURRENT_SCHEMA_VERSION) {
            invalid("Canvas schemaVersion 仅支持 " + CanvasDefinition.CURRENT_SCHEMA_VERSION);
        }
        if (definition.schemaMinorVersion() < CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                || definition.schemaMinorVersion() > CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION) {
            invalid("Canvas schemaMinorVersion 仅支持 "
                    + CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION + " 到 "
                    + CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
        }
        if (definition.schemaMinorVersion() == CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isModelNode)) {
            invalid("MODEL_INPUT 和 MODEL_OUTPUT 从 Canvas 1.1 开始支持");
        }
        if (definition.schemaMinorVersion() < 2
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isRenameNode)) {
            invalid("RENAME 从 Canvas 1.2 开始支持");
        }
        if (definition.schemaMinorVersion() < 3
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isStreamJoinNode)) {
            invalid("STREAM_JOIN 从 Canvas 1.3 开始支持");
        }
        if (definition.schemaMinorVersion() < 4
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isFileDatasetNode)) {
            invalid("FILE_DATASET_INPUT 从 Canvas 1.4 开始支持");
        }
        if (definition.schemaMinorVersion() < 5
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isKafkaNode)) {
            invalid("KAFKA_INPUT 和 KAFKA_OUTPUT 的内联 Value Schema 从 Canvas 1.5 开始支持");
        }
    }

    public CanvasDefinition upgradeToCurrent(CanvasDefinition definition) {
        requireSupportedSource(definition);
        if (definition.schemaMinorVersion() == CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION) {
            return definition;
        }
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                definition.nodes(),
                definition.edges()
        );
    }

    private static boolean isModelNode(CanvasDefinition.CanvasNodeDefinition node) {
        return node instanceof CanvasDefinition.ModelInputNodeDefinition
                || node instanceof CanvasDefinition.ModelOutputNodeDefinition;
    }

    private static boolean isRenameNode(CanvasDefinition.CanvasNodeDefinition node) {
        return node instanceof CanvasDefinition.RenameNodeDefinition;
    }

    private static boolean isStreamJoinNode(CanvasDefinition.CanvasNodeDefinition node) {
        return node instanceof CanvasDefinition.StreamJoinNodeDefinition;
    }

    private static boolean isFileDatasetNode(CanvasDefinition.CanvasNodeDefinition node) {
        return node instanceof CanvasDefinition.FileDatasetInputNodeDefinition;
    }

    private static boolean isKafkaNode(CanvasDefinition.CanvasNodeDefinition node) {
        return node instanceof CanvasDefinition.KafkaInputNodeDefinition
                || node instanceof CanvasDefinition.KafkaOutputNodeDefinition;
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
