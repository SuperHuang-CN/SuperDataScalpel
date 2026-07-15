package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared parser for CSV and TSV files; their public parsing options remain compatible. */
@Component
public class CsvFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.CSV || format == FileDatasetFormat.TSV;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Csv csv)) {
            throw new FileDatasetParsingException("分隔文本解析参数无效");
        }
        if (recordLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }
        InputStream inputStream = FileDatasetParseSource.requireStream(source);
        List<List<String>> records = readRecords(
                new InputStreamReader(inputStream, Charset.forName(csv.charset())), csv, recordLimit + 2
        );
        if (records.isEmpty()) {
            throw new FileDatasetParsingException("分隔文本文件不包含任何记录");
        }

        List<String> headers;
        List<List<String>> dataRecords;
        if (csv.firstRowHeader()) {
            headers = records.getFirst();
            dataRecords = records.subList(1, records.size());
        } else {
            headers = List.of();
            dataRecords = records;
        }
        int maxColumns = Math.max(headers.size(), dataRecords.stream().mapToInt(List::size).max().orElse(0));
        if (maxColumns == 0) {
            throw new FileDatasetParsingException("分隔文本文件不包含字段");
        }
        List<String> columnNames = columnNames(headers, maxColumns);
        boolean truncated = dataRecords.size() > recordLimit;
        List<List<String>> sampledRecords = dataRecords.subList(0, Math.min(recordLimit, dataRecords.size()));
        FieldCollector collector = new FieldCollector();
        columnNames.forEach(collector::ensureField);
        for (List<String> record : sampledRecords) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, LogicalType> types = new LinkedHashMap<>();
            for (int index = 0; index < columnNames.size(); index++) {
                String value = index < record.size() ? record.get(index) : null;
                if (value != null && value.isEmpty()) {
                    value = null;
                }
                row.put(columnNames.get(index), value);
                types.put(columnNames.get(index), FieldCollector.textType(value));
            }
            collector.addRow(row, types);
        }
        return new ParseResult(collector.fields(), collector.rows(), truncated);
    }

    private List<List<String>> readRecords(Reader source, FileDatasetParsingConfiguration.Csv options, int maxRecords)
            throws IOException {
        String delimiter = options.fieldDelimiter();
        if (delimiter == null || delimiter.isEmpty()) {
            throw new FileDatasetParsingException("CSV 字段分隔符不能为空");
        }
        Character quote = character(options.quoteCharacter(), "引号字符");
        Character escape = character(options.escapeCharacter(), "转义字符");
        try (PushbackReader reader = new PushbackReader(source, Math.max(delimiter.length() + 2, 16))) {
            List<List<String>> records = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            int value;
            while (records.size() < maxRecords && (value = reader.read()) >= 0) {
                char character = (char) value;
                if (quoted) {
                    if (quote != null && character == quote) {
                        int next = reader.read();
                        if (next == quote) {
                            current.append(quote);
                        } else {
                            quoted = false;
                            if (next >= 0) {
                                reader.unread(next);
                            }
                        }
                    } else if (escape != null && character == escape) {
                        int next = reader.read();
                        if (next >= 0) {
                            current.append((char) next);
                        } else {
                            current.append(character);
                        }
                    } else {
                        current.append(character);
                    }
                    continue;
                }
                if (quote != null && character == quote && current.isEmpty()) {
                    quoted = true;
                } else if (isFieldDelimiter(reader, character, delimiter)) {
                    fields.add(current.toString());
                    current.setLength(0);
                } else if (RecordReader.isRecordDelimiter(reader, character, options.recordDelimiter())) {
                    fields.add(current.toString());
                    records.add(List.copyOf(fields));
                    fields.clear();
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            if (quoted) {
                throw new FileDatasetParsingException("CSV 文件包含未闭合的引号");
            }
            if (records.size() < maxRecords && (!current.isEmpty() || !fields.isEmpty())) {
                fields.add(current.toString());
                records.add(List.copyOf(fields));
            }
            return records;
        }
    }

    private boolean isFieldDelimiter(PushbackReader reader, char firstCharacter, String delimiter) throws IOException {
        if (firstCharacter != delimiter.charAt(0)) {
            return false;
        }
        if (delimiter.length() == 1) {
            return true;
        }
        char[] tail = new char[delimiter.length() - 1];
        int read = reader.read(tail);
        if (read == tail.length && delimiter.regionMatches(1, new String(tail), 0, tail.length)) {
            return true;
        }
        if (read > 0) {
            reader.unread(tail, 0, read);
        }
        return false;
    }

    private Character character(String value, String label) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) != 1) {
            throw new FileDatasetParsingException("CSV " + label + "只能是一个字符");
        }
        return value.charAt(0);
    }

    private List<String> columnNames(List<String> headers, int maxColumns) {
        Set<String> names = new LinkedHashSet<>();
        List<String> values = new ArrayList<>(maxColumns);
        for (int index = 0; index < maxColumns; index++) {
            String preferred = index < headers.size() && headers.get(index) != null ? headers.get(index).trim() : "";
            if (preferred.isBlank()) {
                preferred = "column_" + (index + 1);
            }
            String value = preferred;
            int suffix = 2;
            while (!names.add(value)) {
                value = preferred + "_" + suffix++;
            }
            values.add(value);
        }
        return values;
    }
}
