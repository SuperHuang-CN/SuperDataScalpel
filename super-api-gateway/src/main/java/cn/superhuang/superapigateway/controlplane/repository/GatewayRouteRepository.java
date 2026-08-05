package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewayRouteEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayRouteRepository extends JpaRepository<GatewayRouteEntity, UUID> {
    boolean existsByCode(String code);
    boolean existsByPathPattern(String pathPattern);
    boolean existsByPathPatternAndIdNot(String pathPattern, UUID id);
    List<GatewayRouteEntity> findAllByServiceIdOrderByOrderAscCodeAsc(UUID serviceId);
    long countByServiceId(UUID serviceId);
    void deleteAllByServiceId(UUID serviceId);
    Optional<GatewayRouteEntity> findBySourceAndExternalId(String source, String externalId);

    @EntityGraph(attributePaths = "methods")
    List<GatewayRouteEntity> findAllByEnabledTrue();
}
