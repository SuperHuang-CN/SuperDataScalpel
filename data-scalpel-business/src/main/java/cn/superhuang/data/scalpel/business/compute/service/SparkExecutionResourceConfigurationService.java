package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** JSON persistence and compatibility defaults for compute-engine Spark resources. */
@Service
public class SparkExecutionResourceConfigurationService {
    private final ObjectMapper objectMapper;

    public SparkExecutionResourceConfigurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SparkExecutionResourcePolicy policy(String value, ComputeBackendType backendType) {
        if (value == null || value.isBlank()) return SparkExecutionResourcePolicy.defaultsFor(toExecutionBackend(backendType));
        try {
            return objectMapper.readValue(value, SparkExecutionResourcePolicy.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("计算引擎运行资源策略损坏", exception);
        }
    }

    public SparkExecutionResourceSpec resources(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, SparkExecutionResourceSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Spark JAR 运行资源配置损坏", exception);
        }
    }

    public String write(SparkExecutionResourcePolicy value) {
        return writeValue(value, "无法保存计算引擎运行资源策略");
    }

    public String write(SparkExecutionResourceSpec value) {
        return writeValue(value, "无法保存 Spark JAR 运行资源配置");
    }

    public static ExecutionBackendType toExecutionBackend(ComputeBackendType backendType) {
        return switch (backendType) {
            case LOCAL_DOCKER -> ExecutionBackendType.LOCAL_DOCKER;
            case YARN -> ExecutionBackendType.YARN;
            case KUBERNETES -> ExecutionBackendType.KUBERNETES;
        };
    }

    private String writeValue(Object value, String failureMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }
}
