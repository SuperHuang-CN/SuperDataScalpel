package cn.superhuang.data.scalpel.business.systemmcp.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "ds_system_mcp_audit")
public class SystemMcpAudit extends BaseEntity {
    @Column(nullable = false, length = 32)
    private String eventType;
    @Column(length = 64)
    private String username;
    @Column
    private UUID tokenId;
    @Column(length = 768)
    private String operationId;
    @Column(length = 50)
    private String toolName;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(length = 100)
    private String errorCode;
    @Column(nullable = false)
    private long durationMs;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String changesJson;
    public SystemMcpAudit() {
    }
    public String getEventType() {
        return eventType;
    }
    public void setEventType(String value) {
        eventType = value;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String value) {
        username = value;
    }
    public UUID getTokenId() {
        return tokenId;
    }
    public void setTokenId(UUID value) {
        tokenId = value;
    }
    public String getOperationId() {
        return operationId;
    }
    public void setOperationId(String value) {
        operationId = value;
    }
    public String getToolName() {
        return toolName;
    }
    public void setToolName(String value) {
        toolName = value;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String value) {
        status = value;
    }
    public String getErrorCode() {
        return errorCode;
    }
    public void setErrorCode(String value) {
        errorCode = value;
    }
    public long getDurationMs() {
        return durationMs;
    }
    public void setDurationMs(long value) {
        durationMs = value;
    }
    public String getChangesJson() {
        return changesJson;
    }
    public void setChangesJson(String value) {
        changesJson = value;
    }
}
