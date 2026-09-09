package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolMonitorResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolSummariesResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcPoolMonitorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceEngineDataSourceMonitoringServiceTest {
    private final ServiceEngineRepository engines = mock(ServiceEngineRepository.class);
    private final ServiceEngineDataSourceRegistrationRepository registrations = mock(ServiceEngineDataSourceRegistrationRepository.class);
    private final ServiceEngineClient client = mock(ServiceEngineClient.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = mock(TransactionStatus.class);
    private final ServiceEngine engine = mock(ServiceEngine.class);
    private final UUID engineId = UUID.randomUUID();
    private final UUID dataSourceId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final ServiceEngineDataSourceMonitoringService service = new ServiceEngineDataSourceMonitoringService(
            engines, registrations, client, transactions
    );

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(engines.findById(engineId)).thenReturn(Optional.of(engine));
        when(engine.getType()).thenReturn(ServiceEngineType.DATASCALPEL);
        when(engine.matchesCode("engine_test")).thenReturn(true);
        var registration = mock(ServiceEngineDataSourceRegistration.class);
        when(registration.getDataSourceId()).thenReturn(dataSourceId);
        when(registration.getEngineId()).thenReturn(engineId);
        when(registrations.findAllByEngineId(engineId)).thenReturn(List.of(registration));
        when(registrations.findById(registrationId)).thenReturn(Optional.of(registration));
    }

    @Test
    void filtersStandaloneStudioDataSourcesAndEndsTransactionBeforeHttp() {
        when(client.dataSourcePoolSummaries(engine)).thenAnswer(invocation -> {
            verify(transactions).commit(transaction);
            return new EngineDataSourcePoolSummariesResponse("engine_test", "sample", List.of(
                    new EngineDataSourcePoolSummariesResponse.Entry(UUID.randomUUID(), JdbcPoolMonitorStatus.UNSUPPORTED, null)
            ));
        });
        var response = service.summaries(engineId);
        assertEquals(1, response.dataSources().size());
        assertEquals(dataSourceId, response.dataSources().getFirst().dataSourceId());
        assertEquals(JdbcPoolMonitorStatus.NOT_LOADED, response.dataSources().getFirst().status());
        assertNull(response.dataSources().getFirst().pool());
        verify(registrations, never()).save(any());
    }

    @Test
    void rejectsMismatchedEngineIdentity() {
        when(client.dataSourcePoolSummaries(engine)).thenReturn(
                new EngineDataSourcePoolSummariesResponse("other_engine", "sample", List.of()));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> service.summaries(engineId)).getStatusCode());
    }

    @Test
    void rejectsMismatchedDataSourceIdentity() {
        when(client.dataSourcePoolMonitor(engine, dataSourceId)).thenReturn(new EngineDataSourcePoolMonitorResponse(
                "engine_test", UUID.randomUUID(), JdbcPoolMonitorStatus.NOT_LOADED, "sample", null,
                List.of(), List.of(), List.of(), List.of()));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> service.detail(registrationId)).getStatusCode());
    }

    @Test
    void oldEngineGetsActionableErrorWithoutChangingRegistrationState() {
        when(client.dataSourcePoolSummaries(engine)).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        var failure = assertThrows(ResponseStatusException.class, () -> service.summaries(engineId));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        assertTrue(failure.getReason().contains("升级并重启"));
        verify(registrations, never()).save(any());
    }

    @Test
    void doesNotContactGeoServerOrUnregisteredSources() {
        when(engine.getType()).thenReturn(ServiceEngineType.GEOSERVER);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.summaries(engineId)).getStatusCode());
        when(registrations.findById(registrationId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.detail(registrationId)).getStatusCode());
        verifyNoInteractions(client);
    }
}
