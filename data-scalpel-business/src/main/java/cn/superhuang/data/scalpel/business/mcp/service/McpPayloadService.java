package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Component
public class McpPayloadService {
    private final ObjectMapper mapper;
    private final int limit;

    public McpPayloadService(ObjectMapper mapper, McpPlatformProperties properties) {
        this.mapper = mapper;
        this.limit = Math.toIntExact(properties.maxPayloadSize().toBytes());
    }

    public String read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "MCP 请求超过大小限制");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String write(Object value) {
        BoundedOutput output = new BoundedOutput(limit);
        try {
            mapper.writeValue(output, value);
            return output.bytes.toString(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            if (output.exceeded) throw new McpScriptExecutionService.McpToolExecutionException("OUTPUT_TOO_LARGE", "Tool 返回结果超过大小限制", exception);
            throw exception;
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private boolean exceeded;
        private BoundedOutput(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException {
            check(1); bytes.write(value);
        }
        @Override public void write(byte[] value, int offset, int length) throws IOException {
            check(length); bytes.write(value, offset, length);
        }
        private void check(int length) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new IOException("MCP serialization interrupted");
            if (length > limit - bytes.size()) {
                exceeded = true;
                throw new IOException("MCP output size exceeded");
            }
        }
    }
}
