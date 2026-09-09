package cn.superhuang.data.scalpel.business.systemmcp.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "ds_system_mcp_api")
public class SystemMcpApi extends BaseEntity {
    @Column(nullable = false, unique = true, length = 768)
    private String operationId;
    @Column(nullable = false, length = 8)
    private String method;
    @Column(nullable = false, length = 750)
    private String path;
    @Column(nullable = false, length = 120)
    private String module;
    @Column(nullable = false, length = 500)
    private String summary;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;
    @Column(length = 2000)
    private String keywords;
    @Column(nullable = false, length = 16)
    private String effect;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(length = 1000)
    private String unavailableReason;
    @Column(nullable = false)
    private boolean enabled;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String contractJson;
    @Column(length = 64)
    private String fingerprint;
    public SystemMcpApi() {
    }
    public String getOperationId() {
        return operationId;
    }
    public void setOperationId(String value) {
        operationId = value;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String value) {
        method = value;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String value) {
        path = value;
    }
    public String getModule() {
        return module;
    }
    public void setModule(String value) {
        module = value;
    }
    public String getSummary() {
        return summary;
    }
    public void setSummary(String value) {
        summary = value;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String value) {
        description = value;
    }
    public String getKeywords() {
        return keywords;
    }
    public void setKeywords(String value) {
        keywords = value;
    }
    public String getEffect() {
        return effect;
    }
    public void setEffect(String value) {
        effect = value;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String value) {
        status = value;
    }
    public String getUnavailableReason() {
        return unavailableReason;
    }
    public void setUnavailableReason(String value) {
        unavailableReason = value;
    }
    public boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(boolean value) {
        enabled = value;
    }
    public String getContractJson() {
        return contractJson;
    }
    public void setContractJson(String value) {
        contractJson = value;
    }
    public String getFingerprint() {
        return fingerprint;
    }
    public void setFingerprint(String value) {
        fingerprint = value;
    }
}
