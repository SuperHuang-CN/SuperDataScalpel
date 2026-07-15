package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsonLinesFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;

    public JsonLinesFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.JSONL;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.JsonLines jsonLines)) {
            throw new FileDatasetParsingException("JSONL 解析参数无效");
        }
        InputStream inputStream = FileDatasetParseSource.requireStream(source);
        List<String> lines = RecordReader.readLines(
                new InputStreamReader(inputStream, Charset.forName(jsonLines.charset())),
                jsonLines.recordDelimiter(), recordLimit + 20
        );
        List<Object> values = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                values.add(objectMapper.readValue(line, Object.class));
            } catch (RuntimeException exception) {
                throw new FileDatasetParsingException("JSONL 第 " + (values.size() + 1) + " 条记录无效：" + safeMessage(exception), exception);
            }
            if (values.size() > recordLimit) {
                break;
            }
        }
        return JsonValueSupport.sample(values, recordLimit, objectMapper);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "格式错误" : message;
    }
}
