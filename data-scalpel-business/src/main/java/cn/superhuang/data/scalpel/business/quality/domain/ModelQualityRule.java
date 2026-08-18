package cn.superhuang.data.scalpel.business.quality.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(
        name = "ds_model_quality_rule",
        indexes = {
                @Index(name = "idx_ds_model_quality_rule_model", columnList = "model_id"),
                @Index(name = "idx_ds_model_quality_rule_reference_model", columnList = "reference_model_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_model_quality_rule_name",
                columnNames = {"model_id", "name"}
        )
)
public class ModelQualityRule extends BaseEntity {

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32, updatable = false)
    private ModelQualityRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModelQualityRuleSeverity severity;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Column(name = "reference_model_id")
    private UUID referenceModelId;

    @Column(name = "invalid_code", length = 64)
    private String invalidCode;

    @Column(name = "invalid_reason", length = 500)
    private String invalidReason;

    protected ModelQualityRule() {
    }

    private ModelQualityRule(
            UUID modelId,
            String name,
            String description,
            ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity,
            boolean enabled,
            String definitionJson,
            UUID referenceModelId
    ) {
        this.modelId = modelId;
        this.ruleType = ruleType;
        update(name, description, severity, definitionJson, referenceModelId);
        this.enabled = enabled;
    }

    public static ModelQualityRule create(
            UUID modelId,
            String name,
            String description,
            ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity,
            boolean enabled,
            String definitionJson,
            UUID referenceModelId
    ) {
        return new ModelQualityRule(
                modelId, name, description, ruleType, severity, enabled, definitionJson, referenceModelId
        );
    }

    public void update(
            String name,
            String description,
            ModelQualityRuleSeverity severity,
            String definitionJson,
            UUID referenceModelId
    ) {
        this.name = normalizeRequired(name);
        this.description = normalizeOptional(description);
        this.severity = severity;
        this.definitionJson = definitionJson;
        this.referenceModelId = referenceModelId;
        clearInvalid();
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void invalidate(String code, String reason) {
        enabled = false;
        invalidCode = code;
        invalidReason = reason;
    }

    public void clearInvalid() {
        invalidCode = null;
        invalidReason = null;
    }

    public UUID getModelId() { return modelId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ModelQualityRuleType getRuleType() { return ruleType; }
    public ModelQualityRuleSeverity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public String getDefinitionJson() { return definitionJson; }
    public UUID getReferenceModelId() { return referenceModelId; }
    public String getInvalidCode() { return invalidCode; }
    public String getInvalidReason() { return invalidReason; }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
