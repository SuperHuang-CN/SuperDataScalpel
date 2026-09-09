package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServerAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface McpServerAccessTokenRepository extends JpaRepository<McpServerAccessToken, UUID> {
    Optional<McpServerAccessToken> findByServerId(UUID serverId);
    void deleteByServerId(UUID serverId);
}
