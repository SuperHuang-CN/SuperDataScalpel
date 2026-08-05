package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewayInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayInstanceRepository extends JpaRepository<GatewayInstanceEntity, String> {
}
