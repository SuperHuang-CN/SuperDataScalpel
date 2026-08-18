package cn.superhuang.data.scalpel.business.assistant.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "ai_llm_model_configuration",
        indexes = {
                @Index(name = "idx_ai_llm_model_enabled", columnList = "enabled,test_status"),
                @Index(name = "idx_ai_llm_model_default", columnList = "default_model")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_llm_model_name", columnNames = "name")
)
public class LlmModelConfiguration extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LlmProtocol protocol;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @Column(name = "api_key_ciphertext", length = 4000)
    private String apiKeyCiphertext;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "extra_request_parameters")
    private String extraRequestParameters;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_model", nullable = false)
    private boolean defaultModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_status", nullable = false, length = 32)
    private LlmModelTestStatus testStatus;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "test_message", length = 500)
    private String testMessage;

    protected LlmModelConfiguration() {
    }

    private LlmModelConfiguration(
            String name,
            LlmProtocol protocol,
            String baseUrl,
            String modelName,
            String apiKeyCiphertext,
            String extraRequestParameters
    ) {
        applyConnection(name, protocol, baseUrl, modelName, apiKeyCiphertext, extraRequestParameters);
        enabled = false;
        defaultModel = false;
        testStatus = LlmModelTestStatus.UNTESTED;
    }

    public static LlmModelConfiguration create(
            String name,
            LlmProtocol protocol,
            String baseUrl,
            String modelName,
            String apiKeyCiphertext,
            String extraRequestParameters
    ) {
        return new LlmModelConfiguration(
                name, protocol, baseUrl, modelName, apiKeyCiphertext, extraRequestParameters
        );
    }

    public void updateConnection(
            String name,
            LlmProtocol protocol,
            String baseUrl,
            String modelName,
            String apiKeyCiphertext,
            String extraRequestParameters
    ) {
        applyConnection(name, protocol, baseUrl, modelName, apiKeyCiphertext, extraRequestParameters);
        enabled = false;
        defaultModel = false;
        testStatus = LlmModelTestStatus.UNTESTED;
        lastTestedAt = null;
        testMessage = null;
    }

    public void updateDisplayName(String name) {
        this.name = requireText(name, "模型显示名称不能为空", 100);
    }

    public void recordTest(LlmModelTestStatus status, String message, Instant testedAt) {
        testStatus = status;
        testMessage = normalizeOptional(message, 500);
        lastTestedAt = testedAt;
        if (status != LlmModelTestStatus.AVAILABLE) {
            enabled = false;
            defaultModel = false;
        }
    }

    public void enable(boolean makeDefault) {
        if (testStatus != LlmModelTestStatus.AVAILABLE) {
            throw new IllegalStateException("模型尚未通过工具调用兼容性测试");
        }
        enabled = true;
        defaultModel = makeDefault;
    }

    public void disable() {
        enabled = false;
        defaultModel = false;
    }

    public void markDefault() {
        if (!enabled || testStatus != LlmModelTestStatus.AVAILABLE) {
            throw new IllegalStateException("只有已启用且可用的模型可以设为默认模型");
        }
        defaultModel = true;
    }

    public void clearDefault() {
        defaultModel = false;
    }

    private void applyConnection(
            String name,
            LlmProtocol protocol,
            String baseUrl,
            String modelName,
            String apiKeyCiphertext,
            String extraRequestParameters
    ) {
        this.name = requireText(name, "模型显示名称不能为空", 100);
        this.protocol = protocol == null ? LlmProtocol.OPENAI_COMPATIBLE : protocol;
        this.baseUrl = requireText(baseUrl, "模型服务地址不能为空", 1000);
        this.modelName = requireText(modelName, "模型标识不能为空", 200);
        this.apiKeyCiphertext = normalizeOptional(apiKeyCiphertext, 4000);
        this.extraRequestParameters = requireText(
                extraRequestParameters, "额外请求参数不能为空", 16_000
        );
    }

    private static String requireText(String value, String message, int maximumLength) {
        String normalized = normalizeOptional(value, maximumLength);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeOptional(String value, int maximumLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("字段长度不能超过 " + maximumLength + " 个字符");
        }
        return normalized;
    }

    public String getName() { return name; }
    public LlmProtocol getProtocol() { return protocol; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public String getExtraRequestParameters() {
        return extraRequestParameters == null || extraRequestParameters.isBlank() ? "{}" : extraRequestParameters;
    }
    public boolean isEnabled() { return enabled; }
    public boolean isDefaultModel() { return defaultModel; }
    public LlmModelTestStatus getTestStatus() { return testStatus; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public String getTestMessage() { return testMessage; }
}
