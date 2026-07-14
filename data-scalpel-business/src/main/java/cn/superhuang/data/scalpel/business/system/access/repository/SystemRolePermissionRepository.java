package cn.superhuang.data.scalpel.business.system.access.repository;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SystemRolePermissionRepository extends JpaRepository<SystemRolePermission, UUID> {

    List<SystemRolePermission> findAllByRoleId(UUID roleId);

    List<SystemRolePermission> findAllByRoleIdIn(Collection<UUID> roleIds);

    void deleteAllByRoleId(UUID roleId);
}
