package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessToken;
import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessTokenServerGrant;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenServerGrantRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerRepository;
import cn.superhuang.data.scalpel.business.mcp.web.request.CreateMcpAccessTokenRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.UpdateMcpAccessTokenRequest;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenDetailResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenIssuedResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenSecretResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAuthorizedServerResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.criteria.Subquery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class McpAccessTokenManagementService {
    private final McpAccessTokenRepository tokens;
    private final McpAccessTokenServerGrantRepository grants;
    private final McpServerRepository servers;
    private final McpTokenService tokenService;
    private final McpCredentialCipher cipher;
    private final SearchEngine searchEngine;

    public McpAccessTokenManagementService(McpAccessTokenRepository tokens,
                                           McpAccessTokenServerGrantRepository grants,
                                           McpServerRepository servers,
                                           McpTokenService tokenService,
                                           McpCredentialCipher cipher,
                                           SearchEngine searchEngine) {
        this.tokens = tokens;
        this.grants = grants;
        this.servers = servers;
        this.tokenService = tokenService;
        this.cipher = cipher;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<McpAccessTokenResponse> search(SearchRequest request) {
        var page = searchEngine.search(request, McpAccessToken.class, tokens);
        Map<UUID, Long> counts = grantCounts(page.getContent().stream().map(McpAccessToken::getId).toList());
        return new PageResponse<>(page.getContent().stream()
                .map(token -> McpAccessTokenResponse.from(token, counts.getOrDefault(token.getId(), 0L))).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    @Transactional(readOnly = true)
    public PageResponse<McpAuthorizedServerResponse> searchServerCandidates(SearchRequest request) {
        var page = searchEngine.search(request, cn.superhuang.data.scalpel.business.mcp.domain.McpServer.class, servers);
        return new PageResponse<>(page.getContent().stream().map(McpAuthorizedServerResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    @Transactional(readOnly = true)
    public McpAccessTokenDetailResponse get(UUID id) {
        McpAccessToken token = requireToken(id);
        List<McpAuthorizedServerResponse> authorized = authorizedServers(id);
        return new McpAccessTokenDetailResponse(McpAccessTokenResponse.from(token, authorized.size()), authorized);
    }

    @Transactional
    public McpAccessTokenIssuedResponse create(CreateMcpAccessTokenRequest request) {
        validateExpiry(request.expiresAt());
        Set<UUID> serverIds = normalized(request.serverIds());
        requireServers(serverIds);
        McpTokenService.IssuedToken issued = tokenService.issue();
        Instant now = Instant.now();
        McpAccessToken token = tokens.saveAndFlush(McpAccessToken.create(request.name(), request.description(),
                request.expiresAt(), issued.digest(), cipher.encrypt(issued.plaintext()), issued.hint(), now));
        saveGrants(token.getId(), serverIds);
        return new McpAccessTokenIssuedResponse(McpAccessTokenResponse.from(token, serverIds.size()), issued.plaintext());
    }

    @Transactional
    public McpAccessTokenResponse update(UUID id, UpdateMcpAccessTokenRequest request) {
        McpAccessToken token = requireToken(id);
        token.update(request.name(), request.description(), request.expiresAt());
        return McpAccessTokenResponse.from(tokens.saveAndFlush(token), grants.findAllByAccessTokenId(id).size());
    }

    @Transactional
    public McpAccessTokenDetailResponse updateServers(UUID id, Set<UUID> requestedServerIds) {
        McpAccessToken token = requireToken(id);
        Set<UUID> serverIds = normalized(requestedServerIds);
        requireServers(serverIds);
        grants.deleteAllByAccessTokenId(id);
        grants.flush();
        saveGrants(id, serverIds);
        return new McpAccessTokenDetailResponse(McpAccessTokenResponse.from(token, serverIds.size()), authorizedServers(id));
    }

    @Transactional
    public McpAccessTokenResponse enable(UUID id) {
        McpAccessToken token = requireToken(id);
        token.enable();
        return response(tokens.saveAndFlush(token));
    }

    @Transactional
    public McpAccessTokenResponse disable(UUID id) {
        McpAccessToken token = requireToken(id);
        token.disable();
        return response(tokens.saveAndFlush(token));
    }

    @Transactional
    public McpAccessTokenIssuedResponse rotate(UUID id) {
        McpAccessToken token = requireToken(id);
        McpTokenService.IssuedToken issued = tokenService.issue();
        token.rotate(issued.digest(), cipher.encrypt(issued.plaintext()), issued.hint(), Instant.now());
        token = tokens.saveAndFlush(token);
        return new McpAccessTokenIssuedResponse(response(token), issued.plaintext());
    }

    @Transactional(readOnly = true)
    public McpAccessTokenSecretResponse secret(UUID id) {
        return new McpAccessTokenSecretResponse(cipher.decrypt(requireToken(id).getTokenCiphertext()));
    }

    @Transactional
    public void delete(UUID id) {
        McpAccessToken token = requireToken(id);
        grants.deleteAllByAccessTokenId(id);
        tokens.delete(token);
    }

    @Transactional(readOnly = true)
    public PageResponse<McpAccessTokenResponse> searchForServer(UUID serverId, SearchRequest request) {
        requireServer(serverId);
        var page = searchEngine.search(request, McpAccessToken.class, tokens, (root, query, builder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var grant = subquery.from(McpAccessTokenServerGrant.class);
            subquery.select(grant.get("accessTokenId")).where(builder.equal(grant.get("serverId"), serverId));
            return root.get("id").in(subquery);
        });
        Map<UUID, Long> counts = grantCounts(page.getContent().stream().map(McpAccessToken::getId).toList());
        return new PageResponse<>(page.getContent().stream()
                .map(token -> McpAccessTokenResponse.from(token, counts.getOrDefault(token.getId(), 0L))).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    @Transactional
    public void grant(UUID serverId, UUID tokenId) {
        requireServer(serverId);
        requireToken(tokenId);
        if (!grants.existsByAccessTokenIdAndServerId(tokenId, serverId)) {
            grants.save(McpAccessTokenServerGrant.create(tokenId, serverId));
        }
    }

    @Transactional
    public void revoke(UUID serverId, UUID tokenId) {
        requireServer(serverId);
        requireToken(tokenId);
        grants.deleteByAccessTokenIdAndServerId(tokenId, serverId);
    }

    private McpAccessTokenResponse response(McpAccessToken token) {
        return McpAccessTokenResponse.from(token, grants.findAllByAccessTokenId(token.getId()).size());
    }

    private List<McpAuthorizedServerResponse> authorizedServers(UUID tokenId) {
        Set<UUID> ids = grants.findAllByAccessTokenId(tokenId).stream()
                .map(McpAccessTokenServerGrant::getServerId).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) return List.of();
        return servers.findAllById(ids).stream().map(McpAuthorizedServerResponse::from)
                .sorted(Comparator.comparing(McpAuthorizedServerResponse::name)).toList();
    }

    private Map<UUID, Long> grantCounts(Collection<UUID> ids) {
        Map<UUID, Long> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (Object[] row : grants.countByAccessTokenIds(ids)) result.put((UUID) row[0], (Long) row[1]);
        return result;
    }

    private void saveGrants(UUID tokenId, Set<UUID> serverIds) {
        if (!serverIds.isEmpty()) grants.saveAll(serverIds.stream()
                .map(serverId -> McpAccessTokenServerGrant.create(tokenId, serverId)).toList());
    }

    private void requireServers(Set<UUID> ids) {
        if (ids.isEmpty()) return;
        Set<UUID> found = new HashSet<>();
        servers.findAllById(ids).forEach(server -> found.add(server.getId()));
        if (found.size() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "授权列表包含不存在的 MCP Server");
    }

    private McpAccessToken requireToken(UUID id) {
        return tokens.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP 访问凭证不存在"));
    }

    private void requireServer(UUID id) {
        if (!servers.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server 不存在");
    }

    private static Set<UUID> normalized(Set<UUID> ids) {
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    private static void validateExpiry(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证有效期必须晚于当前时间");
        }
    }
}
