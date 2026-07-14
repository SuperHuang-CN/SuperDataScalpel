package cn.superhuang.data.scalpel.business.system.access.repository;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemUserRepository extends SearchRepository<SystemUser, UUID> {

    Optional<SystemUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRoleId(UUID roleId);
}
