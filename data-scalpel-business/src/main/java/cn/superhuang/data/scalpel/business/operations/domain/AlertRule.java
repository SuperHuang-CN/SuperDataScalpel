package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_rule")
public class AlertRule extends BaseEntity {
    @Column(unique = true, nullable = false, length = 100)
    private String scopeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertRuleType ruleType;

    @Column()
    private UUID subjectId;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private int thresholdSeconds;

    @Column(nullable = false)
    private int cooldownSeconds;

    @Column(nullable = false)
    private long configurationVersion;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column()
    private String userIdsJson = "[]";

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column()
    private String channelIdsJson = "[]";

    @Column()
    private Instant enabledAt;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType value) { ruleType = value; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID value) { subjectId = value; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity value) { severity = value; }
    public int getThresholdSeconds() { return thresholdSeconds; }
    public void setThresholdSeconds(int value) { thresholdSeconds = value; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int value) { cooldownSeconds = value; }
    public long getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(long value) { configurationVersion = value; }
    public String getUserIdsJson() { return userIdsJson; }
    public void setUserIdsJson(String value) { userIdsJson = value; }
    public String getChannelIdsJson() { return channelIdsJson; }
    public void setChannelIdsJson(String value) { channelIdsJson = value; }
    public Instant getEnabledAt() { return enabledAt; }
    public void setEnabledAt(Instant value) { enabledAt = value; }
}
