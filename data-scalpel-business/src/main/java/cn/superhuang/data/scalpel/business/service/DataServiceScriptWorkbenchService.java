package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceRoutePath;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.request.ExecuteScriptDraftRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptCompletionResponse;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Transaction-free proxy from the management UI to API Studio inside one Service Engine. */
@Service
public class DataServiceScriptWorkbenchService {

    private final ServiceEngineRepository engineRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ServiceEngineDataSourceRegistrationService registrationService;
    private final ServiceEngineClient engineClient;

    public DataServiceScriptWorkbenchService(
            ServiceEngineRepository engineRepository,
            DataSourceRepository dataSourceRepository,
            ServiceEngineDataSourceRegistrationService registrationService,
            ServiceEngineClient engineClient
    ) {
        this.engineRepository = engineRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.registrationService = registrationService;
        this.engineClient = engineClient;
    }

    public ScriptDraftExecutionResponse executeDraft(ExecuteScriptDraftRequest request) {
        ServiceEngine engine = requireEngine(request.engineId());
        DataSource dataSource = requireDataSource(request.dataSourceId());
        registrationService.requireReadyRegistration(engine.getId(), dataSource.getId());
        String routePath = normalizeRoutePath(request.routePath());
        try {
            ScriptDraftExecutionResponse response = engineClient.executeScriptDraft(
                    engine,
                    new ScriptDraftExecutionRequest(
                            dataSource.getId(), routePath, request.script(), request.body(),
                            request.query(), request.headers(), Map.of(), Map.of(), Map.of()
                    )
            );
            if (response == null) {
                throw new IllegalStateException("服务引擎未返回脚本调试结果");
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "脚本调试调用失败：" + safeMessage(exception),
                    exception
            );
        }
    }

    public ScriptCompletionResponse completion(UUID engineId, UUID dataSourceId) {
        ServiceEngine engine = requireEngine(engineId);
        DataSource dataSource = requireDataSource(dataSourceId);
        registrationService.requireReadyRegistration(engine.getId(), dataSource.getId());
        try {
            ScriptCompletionResponse response = engineClient.scriptCompletion(engine, dataSource.getId());
            if (response == null) {
                throw new IllegalStateException("服务引擎未返回脚本补全数据");
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "脚本补全调用失败：" + safeMessage(exception),
                    exception
            );
        }
    }

    private ServiceEngine requireEngine(UUID id) {
        ServiceEngine engine = engineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务引擎不存在"));
        if (!engine.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        }
        return engine;
    }

    private DataSource requireDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本服务数据源不存在"));
        if (!dataSource.isEnabled() || !dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "脚本服务必须绑定已启用的 JDBC 数据源");
        }
        return dataSource;
    }

    private String normalizeRoutePath(String routePath) {
        try {
            return ServiceRoutePath.normalize(routePath);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "服务引擎调用失败"
                : message.substring(0, Math.min(500, message.length()));
    }
}
