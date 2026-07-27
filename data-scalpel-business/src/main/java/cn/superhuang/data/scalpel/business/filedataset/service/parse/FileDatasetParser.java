package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses or fully validates one physical file source. */
public interface FileDatasetParser {

    boolean supports(FileDatasetFormat format);

    default FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.STREAM;
    }

    ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException;

    /**
     * Fully validates a source while retaining at most {@code previewLimit} rows.
     *
     * <p>Schema-backed formats may use their embedded schema and metadata. Weakly structured formats
     * override this method so records after the preview window are still decoded and type-checked.</p>
     */
    default ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) throws IOException {
        return parse(source, configuration, previewLimit);
    }

    /** Discovers logical tables without loading a complete workbook into memory. */
    default List<DiscoveredTable> discoverTables(FileDatasetParseSource source) throws IOException {
        return List.of(new DiscoveredTable("TABLE", 0));
    }

    record ParseResult(
            List<Field> fields,
            List<Map<String, Object>> rows,
            boolean truncated,
            boolean previewSupported,
            Map<String, Object> sourceMetadata,
            long rowCount
    ) {
        public ParseResult(List<Field> fields, List<Map<String, Object>> rows, boolean truncated) {
            this(fields, rows, truncated, true, Map.of(), rows.size());
        }

        public ParseResult(
                List<Field> fields,
                List<Map<String, Object>> rows,
                boolean truncated,
                boolean previewSupported,
                Map<String, Object> sourceMetadata
        ) {
            this(fields, rows, truncated, previewSupported, sourceMetadata, rows.size());
        }

        public ParseResult {
            fields = List.copyOf(fields);
            rows = rows.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
            sourceMetadata = sourceMetadata == null
                    ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(sourceMetadata));
            if (rowCount < rows.size()) {
                throw new IllegalArgumentException("完整记录数不能小于预览记录数");
            }
        }
    }

    record Field(String name, int sortOrder, PlatformTypeDefinition type, boolean nullable) {

        Field(String name, int sortOrder, LogicalType logicalType, boolean nullable) {
            this(name, sortOrder, FileDatasetTypeDefinitions.fromLogicalType(logicalType), nullable);
        }

        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("字段名称不能为空");
            }
            if (sortOrder < 0) {
                throw new IllegalArgumentException("字段顺序不能小于零");
            }
            if (type == null) {
                throw new IllegalArgumentException("字段平台类型不能为空");
            }
            name = name.trim();
        }
    }

    record DiscoveredTable(String sourceName, int sourceOrder) {
        public DiscoveredTable {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalArgumentException("来源表名称不能为空");
            }
            if (sourceOrder < 0) {
                throw new IllegalArgumentException("来源表顺序不能小于零");
            }
            sourceName = sourceName.trim();
        }
    }
}
