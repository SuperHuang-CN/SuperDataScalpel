package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TdEngineTmqInputNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TDENGINE_TMQ_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof TdEngineTmqInputNodeDefinition node)) {
            throw new IllegalArgumentException("TDENGINE_TMQ_INPUT operator received " + definition.nodeType());
        }
        TdEngineTmqInputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(List.of());
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(configuration.topicName(), "请选择 TMQ Topic", "configuration.topicName", issues);
        CanvasNodeSupport.required(configuration.catalogName(), "Topic 数据库不能为空", "configuration.catalogName", issues);
        CanvasNodeSupport.required(configuration.supertableName(), "Topic 超级表不能为空", "configuration.supertableName", issues);
        CanvasNodeSupport.required(
                configuration.topicDefinitionFingerprint(), "Topic 定义指纹不能为空",
                "configuration.topicDefinitionFingerprint", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (configuration.startingOffsets() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择首次启动位置", "configuration.startingOffsets");
        }
        if (configuration.maxOffsetsPerVGroupPerTrigger() == null
                || configuration.maxOffsetsPerVGroupPerTrigger() < 1
                || configuration.maxOffsetsPerVGroupPerTrigger()
                > TdEngineTmqInputConfiguration.MAX_OFFSETS_PER_VGROUP_PER_TRIGGER) {
            issues.error(
                    "INVALID_TMQ_OFFSET_LIMIT", "每 VGroup 每批 Offset 跨度必须在 1 到 1000000 之间",
                    "configuration.maxOffsetsPerVGroupPerTrigger");
        }
        boolean hasEventTimeColumn = configuration.eventTimeColumn() != null
                && !configuration.eventTimeColumn().isBlank();
        boolean hasWatermark = configuration.watermarkDelaySeconds() != null;
        if (hasEventTimeColumn != hasWatermark) {
            issues.error(
                    "TDENGINE_TMQ_EVENT_TIME_CONFIGURATION_INCOMPLETE",
                    "事件时间字段和 Watermark 延迟必须同时配置或同时留空",
                    hasEventTimeColumn
                            ? "configuration.watermarkDelaySeconds"
                            : "configuration.eventTimeColumn"
            );
        }
        if (hasWatermark && (configuration.watermarkDelaySeconds() < 1
                || configuration.watermarkDelaySeconds()
                > TdEngineTmqInputConfiguration.MAX_WATERMARK_DELAY_SECONDS)) {
            issues.error(
                    "INVALID_WATERMARK_DELAY",
                    "Watermark 延迟必须在 1 到 2592000 秒之间",
                    "configuration.watermarkDelaySeconds"
            );
        }

        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.TDENGINE_WEBSOCKET
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "TMQ 输入需要已启用、具有 SOURCE 用途的 TDengine WebSocket 数据源",
                    "configuration.dataSourceId"
            );
        }
        var topic = dataSource == null || configuration.topicName() == null
                ? null : dataSource.tdEngineTmqTopic(configuration.topicName());
        if (dataSource != null && topic == null) {
            issues.error("TDENGINE_TMQ_TOPIC_NOT_FOUND", "元数据快照中不存在 TMQ Topic", "configuration.topicName");
        } else if (topic != null) {
            boolean currentFingerprint = topic.definitionFingerprint()
                    .equals(configuration.topicDefinitionFingerprint());
            boolean legacyFingerprint = topic.legacyDefinitionFingerprint() != null
                    && topic.legacyDefinitionFingerprint()
                    .equals(configuration.topicDefinitionFingerprint());
            if ((!currentFingerprint && !legacyFingerprint)
                    || !topic.catalogName().equals(configuration.catalogName())
                    || !topic.supertableName().equals(configuration.supertableName())) {
                issues.error(
                        "TDENGINE_TMQ_TOPIC_CHANGED", "TMQ Topic 定义或来源已变化",
                        "configuration.topicName");
            } else if (legacyFingerprint) {
                issues.warning(
                        "TDENGINE_TMQ_TOPIC_FINGERPRINT_UPGRADE_RECOMMENDED",
                        "Topic 仍使用旧版结构指纹；重新选择 Topic 后可升级为包含字段结构的 v2 指纹",
                        "configuration.topicDefinitionFingerprint"
                );
            }
            if (hasEventTimeColumn) {
                var eventTimeColumn = topic.columns().stream()
                        .filter(column -> column.name().equals(configuration.eventTimeColumn()))
                        .findFirst()
                        .orElse(null);
                if (eventTimeColumn == null) {
                    issues.error(
                            "COLUMN_NOT_FOUND",
                            "事件时间字段不存在：" + configuration.eventTimeColumn(),
                            "configuration.eventTimeColumn"
                    );
                } else if (eventTimeColumn.fieldType() != PlatformDataType.TIMESTAMP) {
                    issues.error(
                            "EVENT_TIME_COLUMN_TYPE_INVALID",
                            "事件时间字段必须是 TIMESTAMP 类型",
                            "configuration.eventTimeColumn"
                    );
                }
            }
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());

        String eventTimeColumn = hasEventTimeColumn ? configuration.eventTimeColumn() : null;
        String watermarkDelay = hasWatermark
                ? configuration.watermarkDelaySeconds() + " seconds" : null;
        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                CanvasTableOrigin.tdEngineTmq(
                        dataSourceId, configuration.topicName(),
                        configuration.catalogName(), configuration.supertableName()),
                topic.columns(),
                CanvasDatasetKind.UNBOUNDED,
                eventTimeColumn,
                watermarkDelay
        );
        Dataset<Row> dataset = context.dataAccess().readTdEngineTmqInput(node, schema);
        if (eventTimeColumn != null) {
            dataset = dataset.withWatermark(eventTimeColumn, watermarkDelay);
        }
        return CanvasNodeOperationResult.propagated(
                Map.of(schema.name(), new SparkCanvasTable(schema, dataset)), List.of(schema));
    }
}
