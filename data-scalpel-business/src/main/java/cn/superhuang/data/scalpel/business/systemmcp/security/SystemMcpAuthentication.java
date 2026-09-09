package cn.superhuang.data.scalpel.business.systemmcp.security;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import java.util.*;
public final class SystemMcpAuthentication extends AbstractAuthenticationToken {
    private final String username;
    private final UUID tokenId;
    private final transient String bearer;
    public SystemMcpAuthentication(String username,UUID tokenId,String bearer,Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.username=username;
        this.tokenId=tokenId;
        this.bearer=bearer;
        setAuthenticated(true);
    }
    @Override public Object getPrincipal() {
        return username;
    } @Override public Object getCredentials() {
        return null;
    }
    public UUID tokenId() {
        return tokenId;
    } public String forwardingBearer() {
        return bearer;
    }
}
