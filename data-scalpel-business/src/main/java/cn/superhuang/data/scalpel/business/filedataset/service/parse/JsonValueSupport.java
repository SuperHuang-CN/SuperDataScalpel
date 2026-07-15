package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

final class JsonValueSupport {

    private JsonValueSupport() {
    }

    static FileDatasetParser.ParseResult sample(List<?> sourceRecords, int recordLimit, ObjectMapper objectMapper) {
        if (sourceRecords.isEmpty()) {
            throw new FileDatasetParsingException("JSON 文件不包含任何记录");
        }
        boolean truncated = sourceRecords.size() > recordLimit;
        FieldCollector collector = new FieldCollector();
        int last = Math.min(recordLimit, sourceRecords.size());
        for (int index = 0; index < last; index++) {
            addValue(collector, sourceRecords.get(index), objectMapper);
        }
        return new FileDatasetParser.ParseResult(collector.fields(), collector.rows(), truncated);
    }

    static void addValue(FieldCollector collector, Object value, ObjectMapper objectMapper) {
        Map<String, Object> row = new LinkedHashMap<>();
        Map<String, LogicalType> types = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = String.valueOf(entry.getKey());
                Object fieldValue = entry.getValue();
                row.put(name, previewValue(fieldValue, objectMapper));
                types.put(name, FieldCollector.objectType(fieldValue));
            }
        } else {
            row.put("value", previewValue(value, objectMapper));
            types.put("value", FieldCollector.objectType(value));
        }
        collector.addRow(row, types);
    }

    static List<?> asRecords(Object value) {
        if (value instanceof List<?> values) {
            return values;
        }
        if (value instanceof Map<?, ?>) {
            return List.of(value);
        }
        throw new FileDatasetParsingException("JSON 根节点必须是对象或数组");
    }

    private static Object previewValue(Object value, ObjectMapper objectMapper) {
        if (!(value instanceof Map<?, ?> || value instanceof List<?>)) {
            return value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法序列化 JSON 嵌套字段", exception);
        }
    }
}
