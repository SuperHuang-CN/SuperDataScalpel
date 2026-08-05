package cn.superhuang.data.scalpel.business.standard.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "standard_dictionary_item",
        indexes = {
                @Index(
                        name = "idx_standard_dictionary_item_tree",
                        columnList = "dictionary_id,parent_id,sort_order"
                ),
                @Index(name = "idx_standard_dictionary_item_parent", columnList = "parent_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_standard_dictionary_item_code",
                columnNames = {"dictionary_id", "code"}
        )
)
public class StandardDictionaryItem extends BaseEntity {

    @Column(name = "dictionary_id", nullable = false, updatable = false)
    private UUID dictionaryId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 256)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String description;

    protected StandardDictionaryItem() {
    }

    private StandardDictionaryItem(
            UUID dictionaryId,
            UUID parentId,
            String code,
            String name,
            int sortOrder,
            boolean enabled,
            String description
    ) {
        this.dictionaryId = Objects.requireNonNull(dictionaryId, "码表 ID 不能为空");
        this.parentId = parentId;
        this.code = normalizeRequired(code);
        this.name = normalizeRequired(name);
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.description = normalizeOptional(description);
    }

    public static StandardDictionaryItem create(
            UUID dictionaryId,
            UUID parentId,
            String code,
            String name,
            int sortOrder,
            boolean enabled,
            String description
    ) {
        return new StandardDictionaryItem(
                dictionaryId, parentId, code, name, sortOrder, enabled, description
        );
    }

    public boolean update(String code, String name, String description) {
        String normalizedCode = normalizeRequired(code);
        String normalizedName = normalizeRequired(name);
        String normalizedDescription = normalizeOptional(description);
        if (Objects.equals(this.code, normalizedCode)
                && Objects.equals(this.name, normalizedName)
                && Objects.equals(this.description, normalizedDescription)) {
            return false;
        }
        this.code = normalizedCode;
        this.name = normalizedName;
        this.description = normalizedDescription;
        return true;
    }

    public boolean move(UUID parentId, int sortOrder) {
        if (Objects.equals(this.parentId, parentId) && this.sortOrder == sortOrder) {
            return false;
        }
        this.parentId = parentId;
        this.sortOrder = sortOrder;
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

    public UUID getDictionaryId() {
        return dictionaryId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
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
