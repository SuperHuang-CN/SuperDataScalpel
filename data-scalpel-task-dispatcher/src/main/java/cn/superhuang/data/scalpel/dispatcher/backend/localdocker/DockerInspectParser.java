package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class DockerInspectParser {
    private final ObjectMapper mapper;

    public DockerInspectParser(ObjectMapper objectMapper) {
        mapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public DockerContainerInspection parseInspection(String value) throws BackendException {
        try {
            return mapper.readValue(value, DockerContainerInspection.class);
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_DOCKER_RESPONSE", "Docker inspect 返回了无效 JSON", exception);
        }
    }

    public List<String> parseContainerIds(String value) throws BackendException {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        try {
            for (String line : value.lines().toList()) {
                if (line.isBlank()) continue;
                String id = mapper.readValue(line, String.class);
                if (id == null || !id.matches("[0-9a-fA-F]{64}")) {
                    throw new IllegalArgumentException("容器 ID 格式无效");
                }
                result.add(id.toLowerCase());
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_DOCKER_RESPONSE", "Docker ps 返回了无效 JSON", exception);
        }
    }
}
