package cn.superhuang.data.scalpel.engine.accesspolicy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EngineAccessPolicyRepository extends JpaRepository<EngineAccessPolicy, UUID> {

    Optional<EngineAccessPolicy> findByEngineCode(String engineCode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from EngineAccessPolicy p where p.engineCode=:engineCode")
    Optional<EngineAccessPolicy> findForUpdate(String engineCode);
}
