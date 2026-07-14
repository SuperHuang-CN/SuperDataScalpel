package cn.superhuang.data.scalpel.business.system.access.repository;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemRoleRepository extends SearchRepository<SystemRole, UUID> {

    Optional<SystemRole> findByCode(String code);

    boolean existsByCode(String code);
}
