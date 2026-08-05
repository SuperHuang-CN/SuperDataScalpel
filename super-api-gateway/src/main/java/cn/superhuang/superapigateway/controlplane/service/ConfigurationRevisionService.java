package cn.superhuang.superapigateway.controlplane.service;

import cn.superhuang.superapigateway.controlplane.domain.GatewayConfigState;
import cn.superhuang.superapigateway.controlplane.repository.GatewayConfigStateRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationRevisionService {

    private final GatewayConfigStateRepository repository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public ConfigurationRevisionService(
            GatewayConfigStateRepository repository,
            EntityManager entityManager,
            ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long bump() {
        GatewayConfigState state = repository.findForUpdate(GatewayConfigState.SINGLETON_ID)
                .orElseGet(() -> repository.saveAndFlush(
                        new GatewayConfigState(GatewayConfigState.SINGLETON_ID)
                ));
        long revision = state.increment();
        entityManager.createNativeQuery(
                        "select pg_notify('super_api_gateway_config_changed', :payload)",
                        String.class
                )
                .setParameter("payload", Long.toString(revision))
                .getSingleResult();
        events.publishEvent(new ConfigurationChangedEvent(revision));
        return revision;
    }

    @Transactional(readOnly = true)
    public long currentRevision() {
        return repository.findById(GatewayConfigState.SINGLETON_ID)
                .map(GatewayConfigState::getCurrentRevision)
                .orElse(0L);
    }
}
