package cn.superhuang.data.scalpel.business.directory.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** A lightweight, scoped business directory. Child resources keep its UUID as a scalar value. */
@Entity
@Table(name = "ds_directory")
public class Directory extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private DirectoryScope scope;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 500)
    private String description;

    protected Directory() {
    }

    private Directory(DirectoryScope scope, UUID parentId, String name, int sortOrder, String description) {
        this.scope = scope;
        update(parentId, name, sortOrder, description);
    }

    public static Directory create(DirectoryScope scope, UUID parentId, String name, int sortOrder, String description) {
        return new Directory(scope, parentId, name, sortOrder, description);
    }

    public void update(UUID parentId, String name, int sortOrder, String description) {
        this.parentId = parentId;
        this.name = normalizeRequired(name);
        this.sortOrder = sortOrder;
        this.description = normalizeOptional(description);
    }

    public DirectoryScope getScope() {
        return scope;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getDescription() {
        return description;
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
