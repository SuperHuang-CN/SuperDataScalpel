package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Desired and last applied Engine source-address policy managed by the control plane. */
@Entity
@Table(name = "ds_service_engine_access_policy", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_service_engine_access_policy_engine", columnNames = "engine_id"
))
public class ServiceEngineAccessPolicy extends BaseEntity {

    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "allow_cidrs_json", nullable = false)
    private String allowCidrsJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "deny_cidrs_json", nullable = false)
    private String denyCidrsJson;

    @Column(name = "desired_revision", nullable = false)
    private long desiredRevision;

    @Column(name = "applied_revision", nullable = false)
    private long appliedRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ServiceEngineAccessPolicyStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "applied_at")
    private Instant appliedAt;

    protected ServiceEngineAccessPolicy() {
    }

    private ServiceEngineAccessPolicy(UUID engineId, String allowCidrsJson, String denyCidrsJson) {
        this.engineId = engineId;
        this.allowCidrsJson = allowCidrsJson;
        this.denyCidrsJson = denyCidrsJson;
        this.desiredRevision = 1;
        this.status = ServiceEngineAccessPolicyStatus.PENDING;
    }

    public static ServiceEngineAccessPolicy pending(UUID engineId, String allowCidrsJson, String denyCidrsJson) {
        return new ServiceEngineAccessPolicy(engineId, allowCidrsJson, denyCidrsJson);
    }

    public void replace(String allowCidrsJson, String denyCidrsJson) {
        this.allowCidrsJson = allowCidrsJson;
        this.denyCidrsJson = denyCidrsJson;
        this.desiredRevision++;
        this.status = ServiceEngineAccessPolicyStatus.PENDING;
        this.lastError = null;
    }

    public void beginSync() {
        this.status = ServiceEngineAccessPolicyStatus.PENDING;
        this.lastError = null;
    }

    public void ready(long revision) {
        this.appliedRevision = revision;
        this.status = ServiceEngineAccessPolicyStatus.READY;
        this.lastError = null;
        this.appliedAt = Instant.now();
    }

    public void failed(String message) {
        this.status = ServiceEngineAccessPolicyStatus.FAILED;
        this.lastError = message == null || message.isBlank() ? "访问策略同步失败"
                : message.substring(0, Math.min(1000, message.length()));
    }

    public void markOutdated() {
        if (status == ServiceEngineAccessPolicyStatus.NOT_CONFIGURED) return;
        this.status = ServiceEngineAccessPolicyStatus.OUTDATED;
        this.lastError = "服务引擎运行地址或管理身份已变化，请重新同步访问策略";
    }

    public UUID getEngineId() { return engineId; }
    public String getAllowCidrsJson() { return allowCidrsJson; }
    public String getDenyCidrsJson() { return denyCidrsJson; }
    public long getDesiredRevision() { return desiredRevision; }
    public long getAppliedRevision() { return appliedRevision; }
    public ServiceEngineAccessPolicyStatus getStatus() { return status; }
    public String getLastError() { return lastError; }
    public Instant getAppliedAt() { return appliedAt; }
}
