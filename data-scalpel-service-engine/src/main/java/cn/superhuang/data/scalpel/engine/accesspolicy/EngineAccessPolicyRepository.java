package cn.superhuang.data.scalpel.engine.accesspolicy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EngineAccessPolicyRepository extends JpaRepository<EngineAccessPolicy, UUID> {

    Optional<EngineAccessPolicy> findByEngineCode(String engineCode);
}
