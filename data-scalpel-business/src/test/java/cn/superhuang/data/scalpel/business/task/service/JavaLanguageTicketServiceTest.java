package cn.superhuang.data.scalpel.business.task.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaLanguageTicketServiceTest {
    private final SparkJarTaskDefinitionService definitions = mock(SparkJarTaskDefinitionService.class);
    private final TaskCompilationService compilation = mock(TaskCompilationService.class);
    private final JavaLanguageTicketService service = new JavaLanguageTicketService(definitions, compilation);
    private final Instant loginExpiry = Instant.now().plusSeconds(3600);

    @Test void issuesOneUseCredentialAndStablePrivateWorkspaceIdentity() {
        when(compilation.taskEngineBaseUrl()).thenReturn("http://localhost:8091");
        UUID task = UUID.randomUUID(), editor = UUID.randomUUID();
        var first = service.create(task, editor, "alice", loginExpiry);
        var upstream = service.consume(first.ticket()).upstream();
        assertEquals("ws", upstream.getScheme());
        assertEquals(8191, upstream.getPort());
        assertTrue(upstream.getPath().matches("/language/[a-f0-9]{64}"));
        assertFalse(upstream.toString().contains("alice"));
        assertEquals(upstream, service.consume(service.create(task, editor, "alice", loginExpiry).ticket()).upstream());
        assertNotEquals(upstream, service.consume(service.create(task, editor, "bob", loginExpiry).ticket()).upstream());
        assertNotEquals(upstream, service.consume(service.create(task, UUID.randomUUID(), "alice", loginExpiry).ticket()).upstream());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.consume(first.ticket())).getStatusCode().value());
        verify(definitions, times(4)).getOnlineSource(task);
    }

    @Test void rejectsExpiredLoginBeforeReadingTaskAndRejectsMissingTicket() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.create(
                UUID.randomUUID(), UUID.randomUUID(), "alice", Instant.now().minusSeconds(1))).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.consume("missing")).getStatusCode().value());
        verifyNoInteractions(definitions, compilation);
    }

    @Test void boundsPendingHandshakes() {
        when(compilation.taskEngineBaseUrl()).thenReturn("http://localhost:8091");
        UUID task = UUID.randomUUID();
        for (int i = 0; i < 256; i++) service.create(task, UUID.randomUUID(), "alice", loginExpiry);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.create(
                task, UUID.randomUUID(), "alice", loginExpiry)).getStatusCode().value());
    }
}
