package cn.superhuang.data.scalpel.business.system.access.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "sys_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_user_username", columnNames = "username")
)
public class SystemUser extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(nullable = false)
    private boolean enabled;

    protected SystemUser() {
    }

    private SystemUser(String username, String displayName, String passwordHash, UUID roleId, boolean enabled) {
        this.username = normalizeUsername(username);
        this.passwordHash = passwordHash;
        update(displayName, roleId, enabled);
    }

    public static SystemUser create(String username, String displayName, String passwordHash, UUID roleId, boolean enabled) {
        return new SystemUser(username, displayName, passwordHash, roleId, enabled);
    }

    public void update(String displayName, UUID roleId, boolean enabled) {
        this.displayName = required(displayName, "用户名称不能为空");
        if (roleId == null) {
            throw new IllegalArgumentException("用户角色不能为空");
        }
        this.roleId = roleId;
        this.enabled = enabled;
    }

    public void resetPassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String normalizeUsername(String value) {
        return required(value, "用户名不能为空").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
