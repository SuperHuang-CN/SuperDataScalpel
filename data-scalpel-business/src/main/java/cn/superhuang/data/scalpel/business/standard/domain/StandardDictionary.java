package cn.superhuang.data.scalpel.business.standard.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(
        name = "standard_dictionary",
        indexes = @Index(name = "idx_standard_dictionary_updated_at", columnList = "updated_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_standard_dictionary_code", columnNames = "code")
)
public class StandardDictionary extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 32)
    private PlatformDataType valueType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "content_version", nullable = false)
    private int version = 1;

    @Column(length = 500)
    private String description;

    protected StandardDictionary() {
    }

    private StandardDictionary(
            String code,
            String name,
            PlatformDataType valueType,
            boolean enabled,
            String description
    ) {
        this.code = normalizeCode(code);
        this.name = normalizeRequired(name);
        this.valueType = requireSupportedType(valueType);
        this.enabled = enabled;
        this.description = normalizeOptional(description);
    }

    public static StandardDictionary create(
            String code,
            String name,
            PlatformDataType valueType,
            boolean enabled,
            String description
    ) {
        return new StandardDictionary(code, name, valueType, enabled, description);
    }

    public boolean update(String code, String name, PlatformDataType valueType, String description) {
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeRequired(name);
        PlatformDataType normalizedType = requireSupportedType(valueType);
        String normalizedDescription = normalizeOptional(description);
        if (Objects.equals(this.code, normalizedCode)
                && Objects.equals(this.name, normalizedName)
                && this.valueType == normalizedType
                && Objects.equals(this.description, normalizedDescription)) {
            return false;
        }
        this.code = normalizedCode;
        this.name = normalizedName;
        this.valueType = normalizedType;
        this.description = normalizedDescription;
        return true;
    }

    public boolean enable() {
        if (enabled) {
            return false;
        }
        enabled = true;
        return true;
    }

    public boolean disable() {
        if (!enabled) {
            return false;
        }
        enabled = false;
        return true;
    }

    public void advanceVersion() {
        if (version == Integer.MAX_VALUE) {
            throw new IllegalStateException("码表版本号已达到上限");
        }
        version++;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PlatformDataType getValueType() {
        return valueType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public static PlatformDataType requireSupportedType(PlatformDataType valueType) {
        if (valueType == null) {
            throw new IllegalArgumentException("码表取值类型不能为空");
        }
        return switch (valueType) {
            case STRING, INTEGER, LONG, DECIMAL, BOOLEAN -> valueType;
            default -> throw new IllegalArgumentException("码表不支持取值类型：" + valueType);
        };
    }

    public static String normalizeCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("码表编码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("必填内容不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
