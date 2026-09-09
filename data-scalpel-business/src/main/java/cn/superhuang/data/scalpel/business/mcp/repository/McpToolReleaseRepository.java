package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpToolRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpToolReleaseRepository extends JpaRepository<McpToolRelease, UUID> {
    List<McpToolRelease> findAllByReleaseIdOrderBySortOrderAscCodeAsc(UUID releaseId);
    Optional<McpToolRelease> findByReleaseIdAndCode(UUID releaseId, String code);
}
