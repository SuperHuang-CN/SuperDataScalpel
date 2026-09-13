package cn.superhuang.data.scalpel.business.dsh;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import cn.superhuang.data.scalpel.business.dsh.security.DshLoginIdentity;
import cn.superhuang.data.scalpel.business.dsh.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
class DshLeaseReconciliationTest {
    @Test void observedDisableCannotBeUndoneByRapidReenableDuringSameReconciliation() {
        var bridge=mock(DshBridgeClient.class);var identity=mock(DshIdentityService.class);
        var properties=new DshProperties();properties.setEnabled(true);var mapper=JsonMapper.builder().build();
        var bindings=mock(DshBindingService.class);var owner=UUID.randomUUID();var session=UUID.randomUUID();
        var socket=mock(WebSocket.class);
        when(bridge.connect(eq(owner),eq(session),any())).thenReturn(CompletableFuture.completedFuture(socket));
        when(identity.enabled(owner)).thenReturn(false,true);
        when(bridge.get("/bridge/v3/active-users")).thenReturn(mapper.valueToTree(Map.of("userIds",List.of(owner.toString()))));
        var events=new DshEventService(bridge,mock(DshService.class),identity,properties,mapper,bindings);
        try {
            events.subscribe(new DshLoginIdentity(owner,Instant.now().plusSeconds(60)),session);
            events.reconcile();
            verify(bridge).post("/bridge/v3/actions/renew-leases",Map.of("userIds",List.of()));
        } finally { events.stop(); }
    }
}
