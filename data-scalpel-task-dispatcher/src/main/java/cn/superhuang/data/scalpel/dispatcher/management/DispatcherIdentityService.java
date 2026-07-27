package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherIdentity;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DispatcherIdentityService {
    private final DispatcherIdentityRepository repository;

    public DispatcherIdentityService(DispatcherIdentityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID instanceId() {
        return repository.findById("singleton")
                .orElseGet(() -> repository.saveAndFlush(DispatcherIdentity.create()))
                .getInstanceId();
    }
}
