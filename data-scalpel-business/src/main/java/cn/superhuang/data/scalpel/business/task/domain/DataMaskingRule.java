package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.task.MaskingStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "ds_data_masking_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_data_masking_rule_code",
                columnNames = "code"
        ),
        indexes = @Index(name = "idx_data_masking_rule_strategy", columnList = "strategy")
)
public class DataMaskingRule extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaskingStrategy strategy;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    protected DataMaskingRule() {
    }

    private DataMaskingRule(
            String code,
            String name,
            String description,
            MaskingStrategy strategy,
            String definitionJson
    ) {
        this.code = normalizeRequired(code);
        update(name, description, strategy, definitionJson);
    }

    public static DataMaskingRule create(
            String code,
            String name,
            String description,
            MaskingStrategy strategy,
            String definitionJson
    ) {
        return new DataMaskingRule(code, name, description, strategy, definitionJson);
    }

    public void update(
            String name,
            String description,
            MaskingStrategy strategy,
            String definitionJson
    ) {
        if (strategy == null) {
            throw new IllegalArgumentException("脱敏策略不能为空");
        }
        this.name = normalizeRequired(name);
        this.description = normalizeOptional(description);
        this.strategy = strategy;
        this.definitionJson = normalizeRequired(definitionJson);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public MaskingStrategy getStrategy() {
        return strategy;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("必填内容不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
