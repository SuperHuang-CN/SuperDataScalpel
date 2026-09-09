package cn.superhuang.data.scalpel.business.mcp.repository;

import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessToken;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface McpAccessTokenRepository extends SearchRepository<McpAccessToken, UUID> {
    Optional<McpAccessToken> findByTokenDigest(String tokenDigest);

    @Modifying
    @Query("update McpAccessToken t set t.lastUsedAt = :usedAt where t.id = :id and (t.lastUsedAt is null or t.lastUsedAt < :usedAt)")
    int updateLastUsedAt(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
