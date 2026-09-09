package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpTool;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpToolRepository extends SearchRepository<McpTool, UUID> {
    @org.springframework.data.jpa.repository.Query("select t.serverId as serverId, count(t) as toolCount from McpTool t where t.serverId in :ids group by t.serverId")
    List<ServerToolCount> countForServers(@org.springframework.data.repository.query.Param("ids") List<UUID> ids);
    interface ServerToolCount {
        UUID getServerId();
        long getToolCount();
    }
    List<cn.superhuang.data.scalpel.business.mcp.web.response.McpToolSummaryResponse> findSummariesByServerIdOrderBySortOrderAscCodeAsc(UUID serverId);
    List<McpTool> findAllByServerIdOrderBySortOrderAscCodeAsc(UUID serverId);
    List<McpTool> findAllByServerIdAndEnabledTrueOrderBySortOrderAscCodeAsc(UUID serverId);
    Optional<McpTool> findByIdAndServerId(UUID id, UUID serverId);
    boolean existsByServerIdAndCode(UUID serverId, String code);
    boolean existsByServerIdAndCodeAndIdNot(UUID serverId, String code, UUID id);
    long countByServerId(UUID serverId);
    void deleteAllByServerId(UUID serverId);
}
