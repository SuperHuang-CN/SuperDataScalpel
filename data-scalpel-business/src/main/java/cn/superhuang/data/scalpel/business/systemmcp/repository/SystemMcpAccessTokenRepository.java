package cn.superhuang.data.scalpel.business.systemmcp.repository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpAccessToken;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.UUID;
public interface SystemMcpAccessTokenRepository extends SearchRepository<SystemMcpAccessToken, UUID> {
    java.util.List<SystemMcpAccessToken> findByManagedTrue();
    java.util.Optional<SystemMcpAccessToken> findByTokenDigest(String digest);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update SystemMcpAccessToken t set t.lastUsedAt = :now where t.id = :id and t.tokenDigest = :digest and t.enabled = true and (t.expiresAt is null or t.expiresAt > :now)")
    int touch(UUID id, String digest, java.time.Instant now);
}
