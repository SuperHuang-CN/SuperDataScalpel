package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

@Component
public class JsonFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;

    public JsonFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.JSON;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Json json)) {
            throw new FileDatasetParsingException("JSON 解析参数无效");
        }
        InputStream inputStream = FileDatasetParseSource.requireStream(source);
        Object root;
        try (InputStreamReader reader = new InputStreamReader(inputStream, Charset.forName(json.charset()))) {
            root = objectMapper.readValue(reader, Object.class);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("JSON 内容无效：" + safeMessage(exception), exception);
        }
        Object selected = followPointer(root, json.rootPointer());
        return JsonValueSupport.sample(JsonValueSupport.asRecords(selected), recordLimit, objectMapper);
    }

    private Object followPointer(Object root, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return root;
        }
        Object current = root;
        String[] tokens = pointer.substring(1).split("/", -1);
        for (String token : tokens) {
            String decoded = token.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(decoded)) {
                    throw new FileDatasetParsingException("JSON Pointer 指向的节点不存在：" + pointer);
                }
                current = map.get(decoded);
            } else if (current instanceof List<?> list) {
                int index = arrayIndex(decoded, pointer);
                if (index >= list.size()) {
                    throw new FileDatasetParsingException("JSON Pointer 数组下标超出范围：" + pointer);
                }
                current = list.get(index);
            } else {
                throw new FileDatasetParsingException("JSON Pointer 无法继续定位：" + pointer);
            }
        }
        return current;
    }

    private int arrayIndex(String value, String pointer) {
        try {
            if (value.startsWith("-") || (value.length() > 1 && value.startsWith("0"))) {
                throw new NumberFormatException();
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new FileDatasetParsingException("JSON Pointer 包含无效数组下标：" + pointer);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "格式错误" : message;
    }
}
