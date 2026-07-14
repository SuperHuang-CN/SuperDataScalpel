package cn.superhuang.data.scalpel.business.system.access.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sys_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_permission_code", columnNames = "code")
)
public class SystemPermission extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 120)
    private String code;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    protected SystemPermission() {
    }

    private SystemPermission(String code, String module, String name, String description, int sortOrder) {
        this.code = code;
        updateDefinition(module, name, description, sortOrder);
    }

    public static SystemPermission create(String code, String module, String name, String description, int sortOrder) {
        return new SystemPermission(code, module, name, description, sortOrder);
    }

    public void updateDefinition(String module, String name, String description, int sortOrder) {
        this.module = required(module, "权限模块不能为空");
        this.name = required(name, "权限名称不能为空");
        this.description = optional(description);
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public void deactivate() {
        active = false;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
