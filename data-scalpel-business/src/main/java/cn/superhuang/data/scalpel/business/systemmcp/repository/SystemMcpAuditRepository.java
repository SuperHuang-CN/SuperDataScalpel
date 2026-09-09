package cn.superhuang.data.scalpel.business.systemmcp.repository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpAudit;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.UUID;
public interface SystemMcpAuditRepository extends SearchRepository<SystemMcpAudit, UUID> {
    long deleteByCreatedAtBefore(java.time.Instant before);
}
