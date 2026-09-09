package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface McpServerRepository extends SearchRepository<McpServer, UUID> {
    Optional<McpServer> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByDirectoryId(UUID directoryId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from McpServer s where s.id = :id")
    Optional<McpServer> findLockedById(@Param("id") UUID id);
    @Query("select new cn.superhuang.data.scalpel.business.mcp.repository.McpServerRepository$DirectoryResourceCount(s.directoryId, count(s)) from McpServer s where s.directoryId in :ids group by s.directoryId")
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("ids") List<UUID> ids);
    record DirectoryResourceCount(UUID directoryId, long resourceCount) {}
}
