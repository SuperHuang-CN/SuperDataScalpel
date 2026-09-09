package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessTokenServerGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface McpAccessTokenServerGrantRepository extends JpaRepository<McpAccessTokenServerGrant, UUID> {
    boolean existsByAccessTokenIdAndServerId(UUID accessTokenId, UUID serverId);
    List<McpAccessTokenServerGrant> findAllByAccessTokenId(UUID accessTokenId);
    List<McpAccessTokenServerGrant> findAllByAccessTokenIdIn(Collection<UUID> accessTokenIds);
    void deleteAllByAccessTokenId(UUID accessTokenId);
    void deleteAllByServerId(UUID serverId);
    void deleteByAccessTokenIdAndServerId(UUID accessTokenId, UUID serverId);

    @Query("select g.accessTokenId, count(g) from McpAccessTokenServerGrant g where g.accessTokenId in :ids group by g.accessTokenId")
    List<Object[]> countByAccessTokenIds(@Param("ids") Collection<UUID> ids);
}
