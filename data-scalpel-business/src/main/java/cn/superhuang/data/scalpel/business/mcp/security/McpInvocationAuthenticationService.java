package cn.superhuang.data.scalpel.business.mcp.security;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServerStatus;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenServerGrantRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerRepository;
import cn.superhuang.data.scalpel.business.mcp.service.McpTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class McpInvocationAuthenticationService {
    private final McpServerRepository servers; private final McpAccessTokenRepository tokens;
    private final McpAccessTokenServerGrantRepository grants; private final McpTokenService tokenService;
    public McpInvocationAuthenticationService(McpServerRepository servers,McpAccessTokenRepository tokens,
            McpAccessTokenServerGrantRepository grants,McpTokenService tokenService){this.servers=servers;this.tokens=tokens;this.grants=grants;this.tokenService=tokenService;}
    @Transactional(readOnly=true)
    public AuthenticationResult authenticate(String code,String plaintext){
        if(code==null||plaintext==null||!plaintext.startsWith("dsmcp_"))return AuthenticationResult.invalid();
        var token=tokens.findByTokenDigest(tokenService.digest(plaintext)).orElse(null);
        if(token==null||!token.isUsableAt(java.time.Instant.now()))return AuthenticationResult.invalid();
        var server=servers.findByCode(code).orElse(null);
        if(server==null||!grants.existsByAccessTokenIdAndServerId(token.getId(),server.getId()))return AuthenticationResult.forbidden(token.getId());
        return AuthenticationResult.authorized(new AuthenticatedServer(server.getId(),server.getCode(),server.getActiveReleaseId(),
                server.getPublishedVersion(),token.getId(),token.getName(),token.getRevision(),
                server.getStatus()==McpServerStatus.ENABLED&&server.getActiveReleaseId()!=null));
    }
    public record AuthenticationResult(boolean validToken,boolean authorized,UUID tokenId,AuthenticatedServer server){
        static AuthenticationResult invalid(){return new AuthenticationResult(false,false,null,null);}
        static AuthenticationResult forbidden(UUID tokenId){return new AuthenticationResult(true,false,tokenId,null);}
        static AuthenticationResult authorized(AuthenticatedServer server){return new AuthenticationResult(true,true,server.accessTokenId(),server);}
    }
    public record AuthenticatedServer(UUID serverId,String serverCode,UUID releaseId,Integer releaseVersion,
            UUID accessTokenId,String accessTokenName,int tokenRevision,boolean callable){}
}
