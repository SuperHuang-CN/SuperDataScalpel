package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationLog;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface McpInvocationLogRepository extends SearchRepository<McpInvocationLog, UUID> {
    long countByStartedAtGreaterThanEqual(Instant start);
    long countByStartedAtGreaterThanEqualAndStatus(Instant start, cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationStatus status);
    @Query("select coalesce(avg(l.durationMillis),0) from McpInvocationLog l where l.startedAt >= :start")
    double averageDurationSince(Instant start);
    @Modifying
    long deleteByStartedAtBefore(Instant cutoff);
}
