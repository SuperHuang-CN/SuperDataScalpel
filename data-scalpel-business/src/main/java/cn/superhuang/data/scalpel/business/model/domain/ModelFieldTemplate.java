package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;

/** A reusable, copy-only collection of model field definitions. */
@Entity
@Table(
        name = "ds_model_field_template",
        indexes = {
                @Index(name = "idx_ds_model_field_template_category", columnList = "category"),
                @Index(name = "idx_ds_model_field_template_enabled", columnList = "enabled")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_model_field_template_code",
                columnNames = "code"
        )
)
public class ModelFieldTemplate extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "content_version", nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ModelFieldTemplate() {
    }

    private ModelFieldTemplate(
            String code,
            String name,
            String category,
            String description,
            int sortOrder
    ) {
        update(code, name, category, description, sortOrder);
        enabled = true;
    }

    public static ModelFieldTemplate create(
            String code,
            String name,
            String category,
            String description,
            int sortOrder
    ) {
        return new ModelFieldTemplate(code, name, category, description, sortOrder);
    }

    public void update(
            String code,
            String name,
            String category,
            String description,
            int sortOrder
    ) {
        this.code = normalizeRequired(code).toUpperCase(Locale.ROOT);
        this.name = normalizeRequired(name);
        this.category = normalizeOptional(category);
        this.description = normalizeOptional(description);
        this.sortOrder = sortOrder;
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
            throw new IllegalStateException("常用字段模板版本号已达到上限");
        }
        version++;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getVersion() {
        return version;
    }

    public boolean isEnabled() {
        return enabled;
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
