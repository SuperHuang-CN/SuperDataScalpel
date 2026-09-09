package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationLog;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationStatus;
import cn.superhuang.data.scalpel.business.mcp.repository.McpInvocationLogRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenRepository;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpInvocationOverviewResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpInvocationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class McpInvocationLogService {
    private final McpInvocationLogRepository repository; private final McpAccessTokenRepository tokens; private final SearchEngine searchEngine; private final McpPlatformProperties properties;
    public McpInvocationLogService(McpInvocationLogRepository repository,McpAccessTokenRepository tokens,SearchEngine searchEngine,McpPlatformProperties properties){this.repository=repository;this.tokens=tokens;this.searchEngine=searchEngine;this.properties=properties;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID serverId,UUID releaseId,String serverCode,Integer releaseVersion,String toolCode,String type,String protocolVersion,String requestId,
            Instant started,long duration,McpInvocationStatus status,long requestBytes,long responseBytes,String remote,String userAgent,
            UUID accessTokenId,String accessTokenName,Integer tokenRevision,String error){
        repository.save(McpInvocationLog.create(serverId,releaseId,serverCode,releaseVersion,toolCode,type,protocolVersion,requestId,started,duration,status,requestBytes,responseBytes,remote,userAgent,accessTokenId,accessTokenName,tokenRevision,error));
        if(accessTokenId!=null)tokens.updateLastUsedAt(accessTokenId,started);
    }
    @Transactional(readOnly=true) public PageResponse<McpInvocationResponse> search(SearchRequest request){var page=searchEngine.search(request,McpInvocationLog.class,repository);return new PageResponse<>(page.getContent().stream().map(McpInvocationResponse::from).toList(),page.getTotalElements(),page.getTotalPages(),page.getNumber(),page.getSize());}
    @Transactional(readOnly=true) public McpInvocationResponse get(UUID id){return McpInvocationResponse.from(repository.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"调用记录不存在")));}
    @Transactional(readOnly=true) public McpInvocationOverviewResponse overview(){Instant since=Instant.now().minus(24,ChronoUnit.HOURS);long total=repository.countByStartedAtGreaterThanEqual(since);long success=repository.countByStartedAtGreaterThanEqualAndStatus(since,McpInvocationStatus.SUCCESS);return new McpInvocationOverviewResponse(since,total,success,total-success,total==0?0:(double)success/total,repository.averageDurationSince(since));}
    @Scheduled(cron="${data-scalpel.mcp.invocation-cleanup-cron:0 45 3 * * *}") @Transactional public void cleanup(){repository.deleteByStartedAtBefore(Instant.now().minus(properties.invocationRetention()));}
}
