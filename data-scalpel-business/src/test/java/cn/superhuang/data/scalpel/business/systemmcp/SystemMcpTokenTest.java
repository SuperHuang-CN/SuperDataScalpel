package cn.superhuang.data.scalpel.business.systemmcp;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SystemMcpTokenTest {
    @Test void grantsMcpPermissionsAndRejectsRevokedOrDisabledIdentity() {
        var tokens=mock(SystemMcpAccessTokenRepository.class);
        var users=mock(SystemUserRepository.class);
        var token=new SystemMcpAccessToken();
        UUID userId=UUID.randomUUID();
        token.setUserId(userId);
        token.setEnabled(true);
        token.setTokenDigest(SystemMcpTokenService.digest("dssmcp_test"));
        var user=mock(SystemUser.class);
        when(user.getUsername()).thenReturn("reader");
        when(user.isEnabled()).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(tokens.findByTokenDigest(token.getTokenDigest())).thenReturn(Optional.of(token));
        when(tokens.touch(any(),eq(token.getTokenDigest()),any())).thenReturn(1);
        var service=new SystemMcpTokenService(tokens,users);
        assertTrue(service.authenticate("dssmcp_test").getAuthorities().stream().anyMatch(a->a.getAuthority().equals("model.view")));
        assertTrue(service.authenticate("dssmcp_test").getAuthorities().stream().anyMatch(a->a.getAuthority().equals("task.view")));
        token.setEnabled(false);
        assertThrows(BadCredentialsException.class,()->service.authenticate("dssmcp_test"));
        token.setEnabled(true);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(BadCredentialsException.class,()->service.authenticate("dssmcp_test"));
        token.setExpiresAt(null);
        when(user.isEnabled()).thenReturn(false);
        assertThrows(BadCredentialsException.class,()->service.authenticate("dssmcp_test"));
        when(user.isEnabled()).thenReturn(true);
        when(tokens.touch(any(),eq(token.getTokenDigest()),any())).thenReturn(0);
        assertThrows(BadCredentialsException.class,()->service.authenticate("dssmcp_test"));
        assertThrows(BadCredentialsException.class,()->service.authenticate("mcp_other"));
    }
}
