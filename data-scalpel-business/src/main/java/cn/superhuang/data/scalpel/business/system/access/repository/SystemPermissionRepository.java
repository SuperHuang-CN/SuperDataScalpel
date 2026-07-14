package cn.superhuang.data.scalpel.business.system.access.repository;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermission;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemPermissionRepository extends SearchRepository<SystemPermission, UUID> {

    Optional<SystemPermission> findByCode(String code);
}
