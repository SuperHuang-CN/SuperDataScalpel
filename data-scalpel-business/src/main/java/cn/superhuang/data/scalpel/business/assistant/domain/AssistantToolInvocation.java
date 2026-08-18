package cn.superhuang.data.scalpel.business.assistant.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "ai_assistant_tool_invocation", indexes = @Index(name = "idx_ai_tool_run", columnList = "run_id,created_at"))
public class AssistantToolInvocation extends BaseEntity {

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "tool_name", nullable = false, updatable = false, length = 100)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private AssistantToolRisk risk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private AssistantToolStatus status;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "arguments_json", nullable = false, updatable = false)
    private String argumentsJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "result_json", nullable = false, updatable = false)
    private String resultJson;

    protected AssistantToolInvocation() {
    }

    private AssistantToolInvocation(
            UUID runId,
            String toolName,
            AssistantToolRisk risk,
            AssistantToolStatus status,
            String argumentsJson,
            String resultJson
    ) {
        this.runId = java.util.Objects.requireNonNull(runId);
        this.toolName = toolName;
        this.risk = risk;
        this.status = status;
        this.argumentsJson = argumentsJson;
        this.resultJson = resultJson;
    }

    public static AssistantToolInvocation record(
            UUID runId,
            String toolName,
            AssistantToolRisk risk,
            AssistantToolStatus status,
            String argumentsJson,
            String resultJson
    ) {
        return new AssistantToolInvocation(runId, toolName, risk, status, argumentsJson, resultJson);
    }

    public UUID getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public AssistantToolRisk getRisk() { return risk; }
    public AssistantToolStatus getStatus() { return status; }
    public String getArgumentsJson() { return argumentsJson; }
    public String getResultJson() { return resultJson; }
}
