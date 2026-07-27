package cn.superhuang.data.scalpel.dispatcher.web.resource;

import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.web.response.DispatcherExecutionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task-executions")
public class DispatcherExecutionResource {
    private final DispatcherTaskExecutionRepository repository;

    public DispatcherExecutionResource(DispatcherTaskExecutionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{executionId}")
    public DispatcherExecutionResponse get(@PathVariable UUID executionId) {
        return repository.findByExecutionId(executionId).map(DispatcherExecutionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "执行不存在"));
    }
}
