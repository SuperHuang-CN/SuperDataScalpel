package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServerRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpServerReleaseRepository extends JpaRepository<McpServerRelease, UUID> {
    List<McpServerRelease> findAllByServerIdOrderByVersionDesc(UUID serverId);
    Optional<McpServerRelease> findByServerIdAndVersion(UUID serverId, int version);
    Optional<McpServerRelease> findFirstByServerIdOrderByVersionDesc(UUID serverId);
}
