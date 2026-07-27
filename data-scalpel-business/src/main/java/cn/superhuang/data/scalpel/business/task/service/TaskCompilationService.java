package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskCompilationCancellationResponse;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
public class TaskCompilationService {

    public static final String TASK_ENGINE_BASE_URL_KEY = "task.engine.base-url";

    private final SystemConfigurationService systemConfigurationService;
    private final TaskEngineClient taskEngineClient;

    public TaskCompilationService(
            SystemConfigurationService systemConfigurationService,
            TaskEngineClient taskEngineClient
    ) {
        this.systemConfigurationService = systemConfigurationService;
        this.taskEngineClient = taskEngineClient;
    }

    public TaskCompilationResponse compile(TaskCompilationRequest request) {
        return taskEngineClient.compile(taskEngineBaseUrl(), request);
    }

    public TaskCompilationCancellationResponse cancel(UUID requestId) {
        return taskEngineClient.cancel(taskEngineBaseUrl(), requestId);
    }

    private String taskEngineBaseUrl() {
        String configured = systemConfigurationService.requireValue(TASK_ENGINE_BASE_URL_KEY).trim();
        URI uri;
        try {
            uri = URI.create(configured);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Task Engine 地址配置无效",
                    exception
            );
        }
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Task Engine 地址配置无效"
            );
        }
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }
}
