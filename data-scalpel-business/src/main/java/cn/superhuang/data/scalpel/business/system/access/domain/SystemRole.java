package cn.superhuang.data.scalpel.business.system.access.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;

@Entity
@Table(
        name = "sys_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_role_code", columnNames = "code")
)
public class SystemRole extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    protected SystemRole() {
    }

    private SystemRole(String code, String name, String description, boolean builtIn) {
        this.code = normalizeCode(code);
        this.builtIn = builtIn;
        update(name, description);
    }

    public static SystemRole create(String code, String name, String description, boolean builtIn) {
        return new SystemRole(code, name, description, builtIn);
    }

    public void update(String name, String description) {
        this.name = normalizeRequired(name, "角色名称不能为空");
        this.description = normalizeOptional(description);
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

    public boolean isBuiltIn() {
        return builtIn;
    }

    private static String normalizeCode(String value) {
        return normalizeRequired(value, "角色编码不能为空").toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
