package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses a bounded sample from one physical file format. */
public interface FileDatasetParser {

    boolean supports(FileDatasetFormat format);

    default FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.STREAM;
    }

    ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException;

    record ParseResult(List<Field> fields, List<Map<String, Object>> rows, boolean truncated) {
        public ParseResult {
            fields = List.copyOf(fields);
            rows = rows.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
        }
    }

    record Field(String name, int sortOrder, LogicalType logicalType, boolean nullable) {
    }
}
