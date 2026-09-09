package cn.superhuang.data.scalpel.business.systemmcp.repository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpApi;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.UUID;
public interface SystemMcpApiRepository extends SearchRepository<SystemMcpApi, UUID> {
    java.util.Optional<SystemMcpApi> findByOperationId(String operationId);
    java.util.List<SystemMcpApi> findAllByEnabledTrueAndStatus(String status);
}
