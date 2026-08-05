package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;

/** A globally configured warehouse layer that can be assigned to data models. */
@Entity
@Table(
        name = "ds_model_warehouse_layer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_model_warehouse_layer_code",
                columnNames = "code"
        )
)
public class ModelWarehouseLayer extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 7)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "model_code_prefix", length = 32)
    private String modelCodePrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_layer_policy", length = 32)
    private ModelWarehouseLayerInputPolicy inputLayerPolicy;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ModelWarehouseLayer() {
    }

    private ModelWarehouseLayer(
            String code,
            String name,
            String description,
            String color,
            int sortOrder
    ) {
        update(code, name, description, color, sortOrder);
        enabled = true;
    }

    public static ModelWarehouseLayer create(
            String code,
            String name,
            String description,
            String color,
            int sortOrder
    ) {
        return new ModelWarehouseLayer(code, name, description, color, sortOrder);
    }

    public static ModelWarehouseLayer create(
            String code,
            String name,
            String description,
            String color,
            int sortOrder,
            String modelCodePrefix,
            ModelWarehouseLayerInputPolicy inputLayerPolicy
    ) {
        ModelWarehouseLayer layer = new ModelWarehouseLayer(code, name, description, color, sortOrder);
        layer.configureModelingRules(modelCodePrefix, inputLayerPolicy);
        return layer;
    }

    public void update(
            String code,
            String name,
            String description,
            String color,
            int sortOrder
    ) {
        this.code = normalizeCode(code);
        this.name = normalizeRequired(name);
        this.description = normalizeOptional(description);
        this.color = normalizeColor(color);
        this.sortOrder = sortOrder;
    }

    public void configureModelingRules(
            String modelCodePrefix,
            ModelWarehouseLayerInputPolicy inputLayerPolicy
    ) {
        this.modelCodePrefix = normalizeOptional(modelCodePrefix);
        this.inputLayerPolicy = inputLayerPolicy == null
                ? ModelWarehouseLayerInputPolicy.UNRESTRICTED
                : inputLayerPolicy;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
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

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getModelCodePrefix() {
        return modelCodePrefix;
    }

    public ModelWarehouseLayerInputPolicy getInputLayerPolicy() {
        return inputLayerPolicy == null
                ? ModelWarehouseLayerInputPolicy.UNRESTRICTED
                : inputLayerPolicy;
    }

    public boolean hasConfiguredInputLayerPolicy() {
        return inputLayerPolicy != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String normalizeCode(String value) {
        return normalizeRequired(value).toUpperCase(Locale.ROOT);
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

    private static String normalizeColor(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
