package cn.superhuang.data.scalpel.business.dsh;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpAccessToken;
import cn.superhuang.data.scalpel.business.systemmcp.repository.SystemMcpAccessTokenRepository;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpTokenService;
import cn.superhuang.data.scalpel.business.systemmcp.web.request.UpdateSystemMcpTokenRequest;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import org.junit.jupiter.api.Test;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DshManagedTokenTest {
    @Test void everyManualMutationRejectsManagedTokensWhileLegacyNullRemainsManual() {
        var repository=mock(SystemMcpAccessTokenRepository.class);
        var service=new SystemMcpTokenService(repository,mock(SystemUserRepository.class),mock(SystemAccessService.class));
        var token=new SystemMcpAccessToken();UUID id=UUID.randomUUID();when(repository.findById(id)).thenReturn(Optional.of(token));
        assertFalse(token.isManaged());token.setManaged(true);
        for (Runnable action: List.<Runnable>of(()->service.rotate(id),()->service.enabled(id,true),()->service.enabled(id,false),()->service.delete(id),()->service.update(id,new UpdateSystemMcpTokenRequest("name",null)))) {
            var error=assertThrows(CodedProblemException.class,action::run);
            assertEquals(409,error.status().value());assertEquals("SYSTEM_MCP_TOKEN_MANAGED",error.code());
        }
        verify(repository,never()).delete(any(SystemMcpAccessToken.class));token.setManaged(false);service.enabled(id,false);assertFalse(token.getEnabled());
    }
    @Test void managedStatusFollowsDisabledDeletedAndReenabledUsersWithoutChangingSecret() {
        var repository=mock(SystemMcpAccessTokenRepository.class);var users=mock(SystemUserRepository.class);
        var service=new SystemMcpTokenService(repository,users,mock(SystemAccessService.class));
        UUID id=UUID.randomUUID();var token=new SystemMcpAccessToken();token.setUserId(id);token.setManaged(true);token.setEnabled(true);token.setTokenDigest("fixed");
        when(repository.findByManagedTrue()).thenReturn(List.of(token));when(users.findAllById(any())).thenReturn(List.of());
        assertEquals(1,service.reconcileManagedStates().size());assertFalse(token.getEnabled());
        var user=mock(cn.superhuang.data.scalpel.business.system.access.domain.SystemUser.class);when(user.getId()).thenReturn(id);when(user.isEnabled()).thenReturn(true);when(user.getUsername()).thenReturn("reader");
        when(users.findAllById(any())).thenReturn(List.of(user));service.reconcileManagedStates();assertTrue(token.getEnabled());assertEquals("fixed",token.getTokenDigest());
        when(user.isEnabled()).thenReturn(false);service.reconcileManagedStates();assertFalse(token.getEnabled());
    }
}
