package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TextFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.TXT;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        return read(source, configuration, recordLimit, false);
    }

    @Override
    public ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) throws IOException {
        return read(source, configuration, previewLimit, true);
    }

    private ParseResult read(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit,
            boolean validateAll
    ) throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Text text)) {
            throw new FileDatasetParsingException("TXT 解析参数无效");
        }
        InputStream inputStream = FileDatasetParseSource.requireStream(source);
        if (validateAll) {
            FieldCollector collector = new FieldCollector(recordLimit);
            RecordReader.forEachLine(
                    new InputStreamReader(inputStream, Charset.forName(text.charset())),
                    text.recordDelimiter(),
                    record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("value", record);
                        collector.addRow(row, Map.of("value", LogicalType.STRING));
                    }
            );
            if (collector.rowCount() == 0) {
                throw new FileDatasetParsingException("TXT 文件不包含任何记录");
            }
            return new ParseResult(
                    collector.fields(), collector.rows(), collector.rowCount() > recordLimit,
                    true, Map.of(), collector.rowCount()
            );
        }
        List<String> records = RecordReader.readLines(
                new InputStreamReader(inputStream, Charset.forName(text.charset())), text.recordDelimiter(), recordLimit + 1
        );
        if (records.isEmpty()) {
            throw new FileDatasetParsingException("TXT 文件不包含任何记录");
        }
        boolean truncated = records.size() > recordLimit;
        FieldCollector collector = new FieldCollector();
        for (String record : records.subList(0, Math.min(recordLimit, records.size()))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", record);
            collector.addRow(row, Map.of("value", LogicalType.STRING));
        }
        return new ParseResult(collector.fields(), collector.rows(), truncated);
    }
}
