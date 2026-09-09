package cn.superhuang.data.scalpel.business.systemmcp;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.business.systemmcp.security.*;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.method.HandlerMethod;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SystemMcpAuthorizationTest {
    public static class Resource {
        @PreAuthorize("hasRole('super_admin') or hasAuthority('model.view')") public void get() {
        }
    }
    @Test void evaluatesActualSpringExpressionsAndRechecksExposure()throws Exception {
        var catalog=mock(SystemMcpCatalogService.class);
        var repo=mock(SystemMcpApiRepository.class);
        var a=new SystemMcpApi();
        a.setOperationId("GET /api/v1/models/{id}");
        a.setEnabled(true);
        a.setStatus("AVAILABLE");
        when(catalog.handler(a.getOperationId())).thenReturn(new HandlerMethod(new Resource(),Resource.class.getMethod("get")));
        var service=new SystemMcpAuthorization(catalog,repo,new StaticApplicationContext());
        var allowed=new UsernamePasswordAuthenticationToken("reader",null,List.of(new SimpleGrantedAuthority("model.view")));
        var denied=new UsernamePasswordAuthenticationToken("other",null,List.of(new SimpleGrantedAuthority("model.update")));
        assertTrue(service.permitted(a,allowed));
        assertFalse(service.permitted(a,denied));
        a.setEnabled(false);
        assertFalse(service.permitted(a,allowed));
        a.setEnabled(true);
        a.setStatus("REMOVED");
        assertFalse(service.permitted(a,allowed));
    }
    @Test void tokenNeverIncludesBearerInAuthenticationDiagnostics() {
        var a=new SystemMcpAuthentication("user",UUID.randomUUID(),"dssmcp_secret",List.of());
        assertNull(a.getCredentials());
        assertFalse(a.toString().contains("dssmcp_secret"));
    }
}
