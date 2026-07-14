package cn.superhuang.data.scalpel.business.system.access.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "sys_role_permission",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_role_permission_role_permission",
                columnNames = {"role_id", "permission_id"}
        )
)
public class SystemRolePermission extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    protected SystemRolePermission() {
    }

    private SystemRolePermission(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public static SystemRolePermission create(UUID roleId, UUID permissionId) {
        return new SystemRolePermission(roleId, permissionId);
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }
}
