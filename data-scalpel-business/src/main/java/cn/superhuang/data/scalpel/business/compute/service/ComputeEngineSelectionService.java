package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ComputeEngineSelectionService {

    private final ComputeEngineRepository repository;

    public ComputeEngineSelectionService(ComputeEngineRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ComputeEngine requireExisting(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark Canvas 任务必须选择计算引擎");
        }
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "计算引擎不存在"));
    }

    @Transactional(readOnly = true)
    public ComputeEngine requireActive(UUID id) {
        ComputeEngine engine = requireExisting(id);
        if (engine.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎未激活：" + engine.getName());
        }
        if (engine.getHealthState() == ComputeEngineHealthState.DOWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎当前不可用：" + engine.getName());
        }
        return engine;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> names(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(ComputeEngine::getId, ComputeEngine::getName));
    }
}
