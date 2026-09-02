package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class KafkaValueSchemaSupport {

    private KafkaValueSchemaSupport() {
    }

    static List<CanvasColumnSchema> inputColumns(
            KafkaInputConfiguration configuration,
            CanvasNodeIssueSink issues,
            String path
    ) {
        KafkaInputValueFormat valueFormat = configuration.effectiveValueFormat();
        List<CanvasColumnSchema> columns = new ArrayList<>();
        if (valueFormat == KafkaInputValueFormat.JSON) {
            columns.addAll(columns(configuration.valueSchema(), issues, path + ".valueSchema"));
        } else {
            KafkaValueSchema schema = configuration.valueSchema();
            if (schema == null || schema.columns() == null) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "TEXT/BINARY 格式的 Value Schema 必须为空数组",
                        path + ".valueSchema.columns"
                );
            } else if (!schema.columns().isEmpty()) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_NOT_APPLICABLE",
                        "TEXT/BINARY 格式不使用 Value Schema",
                        path + ".valueSchema.columns"
                );
            }
            columns.add(new CanvasColumnSchema(
                    "value",
                    valueFormat == KafkaInputValueFormat.TEXT
                            ? PlatformDataType.STRING : PlatformDataType.BINARY,
                    null,
                    null,
                    null,
                    true,
                    null,
                    false,
                    false,
                    "Kafka 消息 Value"
            ));
        }

        List<KafkaInputMetadataField> configuredMetadataFields = configuration.effectiveMetadataFields();
        Set<KafkaInputMetadataField> selected = new LinkedHashSet<>();
        for (int index = 0; index < configuredMetadataFields.size(); index++) {
            KafkaInputMetadataField field = configuredMetadataFields.get(index);
            if (field == null) {
                issues.error(
                        "KAFKA_METADATA_FIELD_INVALID",
                        "Kafka 元数据字段不能为空",
                        path + ".metadataFields[" + index + "]"
                );
            } else if (!selected.add(field)) {
                issues.error(
                        "KAFKA_METADATA_FIELD_DUPLICATE",
                        "Kafka 元数据字段重复配置：" + field,
                        path + ".metadataFields[" + index + "]"
                );
            }
        }

        Set<String> names = new HashSet<>();
        columns.forEach(column -> names.add(column.name()));
        for (KafkaInputMetadataField field : KafkaInputMetadataField.values()) {
            if (!selected.contains(field)) continue;
            CanvasColumnSchema metadataColumn = metadataColumn(field);
            if (!names.add(metadataColumn.name())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "Kafka Value 字段与元数据字段重名：" + metadataColumn.name(),
                        path + ".metadataFields"
                );
                continue;
            }
            columns.add(metadataColumn);
        }
        return List.copyOf(columns);
    }

    static List<CanvasColumnSchema> columns(
            KafkaValueSchema schema,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (schema == null) {
            issues.error("KAFKA_VALUE_SCHEMA_REQUIRED", "请定义 Kafka Value Schema", path);
            return List.of();
        }
        if (schema.columns().isEmpty()) {
            issues.error("KAFKA_VALUE_SCHEMA_EMPTY", "Kafka Value Schema 至少需要一个字段", path + ".columns");
            return List.of();
        }

        List<CanvasColumnSchema> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < schema.columns().size(); index++) {
            KafkaValueColumn column = schema.columns().get(index);
            String columnPath = path + ".columns[" + index + "]";
            if (column == null) {
                issues.error("KAFKA_VALUE_SCHEMA_INVALID", "Kafka Value Schema 字段不能为空", columnPath);
                continue;
            }
            if (CanvasNodeSupport.blank(column.name()) || column.name().length() > 255) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "Kafka Value Schema 字段名长度必须为 1 到 255",
                        columnPath + ".name"
                );
                continue;
            }
            if (!names.add(column.name())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "Kafka Value Schema 字段名重复：" + column.name(),
                        columnPath + ".name"
                );
                continue;
            }
            if (column.fieldType() == null) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "请选择 Kafka Value Schema 字段类型",
                        columnPath + ".fieldType"
                );
                continue;
            }
            if (column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "SPATIAL_FIELD_UNSUPPORTED",
                        "Kafka Value Schema 第一版不支持空间字段",
                        columnPath + ".fieldType"
                );
                continue;
            }
            if (!validTypeParameters(column, issues, columnPath)) {
                continue;
            }
            columns.add(new CanvasColumnSchema(
                    column.name(),
                    column.fieldType(),
                    column.length(),
                    column.precision(),
                    column.scale(),
                    column.nullable(),
                    null,
                    false,
                    false,
                    column.comment()
            ));
        }
        return List.copyOf(columns);
    }

    private static boolean validTypeParameters(
            KafkaValueColumn column,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (column.fieldType() == PlatformDataType.STRING) {
            if (column.length() != null && column.length() < 1) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "STRING 长度必须大于 0",
                        path + ".length"
                );
                return false;
            }
            if (column.precision() != null || column.scale() != null) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "STRING 字段不能设置 precision 或 scale",
                        path
                );
                return false;
            }
            return true;
        }
        if (column.fieldType() == PlatformDataType.DECIMAL) {
            if (column.precision() == null || column.precision() < 1 || column.precision() > 38
                    || column.scale() == null || column.scale() < 0
                    || column.scale() > column.precision()) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "DECIMAL precision 必须为 1 到 38，scale 必须为 0 到 precision",
                        path
                );
                return false;
            }
            if (column.length() != null) {
                issues.error(
                        "KAFKA_VALUE_SCHEMA_INVALID",
                        "DECIMAL 字段不能设置 length",
                        path + ".length"
                );
                return false;
            }
            return true;
        }
        if (column.length() != null || column.precision() != null || column.scale() != null) {
            issues.error(
                    "KAFKA_VALUE_SCHEMA_INVALID",
                    column.fieldType() + " 字段不能设置 length、precision 或 scale",
                    path
            );
            return false;
        }
        return true;
    }

    private static CanvasColumnSchema metadataColumn(KafkaInputMetadataField field) {
        return switch (field) {
            case KEY -> scalar("_kafka_key", PlatformDataType.BINARY, "Kafka 消息 Key");
            case TOPIC -> scalar("_kafka_topic", PlatformDataType.STRING, "Kafka Topic");
            case PARTITION -> scalar("_kafka_partition", PlatformDataType.INTEGER, "Kafka Partition");
            case OFFSET -> scalar("_kafka_offset", PlatformDataType.LONG, "Kafka Offset");
            case TIMESTAMP -> scalar("_kafka_timestamp", PlatformDataType.TIMESTAMP, "Kafka 消息时间");
        };
    }

    private static CanvasColumnSchema scalar(String name, PlatformDataType type, String comment) {
        return new CanvasColumnSchema(
                name, type, null, null, null, true,
                null, false, false, comment
        );
    }
}
