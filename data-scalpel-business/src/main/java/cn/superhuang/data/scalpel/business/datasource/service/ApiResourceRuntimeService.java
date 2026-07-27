package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.http.HttpApiConnectorRegistry;
import cn.superhuang.data.scalpel.business.datasource.service.http.HttpApiExecutionException;
import cn.superhuang.data.scalpel.business.datasource.service.http.PullResult;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiTestDiagnosticResponse;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Executes resource tests outside management-database transactions. */
@Service
public class ApiResourceRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiResourceRuntimeService.class);
    private static final int PREVIEW_ROWS = 20;

    private final DataSourceRepository dataSourceRepository;
    private final ApiResourceService resourceService;
    private final DataSourceRuntimeService dataSourceRuntimeService;
    private final HttpApiConnectorRegistry connectors;

    public ApiResourceRuntimeService(
            DataSourceRepository dataSourceRepository,
            ApiResourceService resourceService,
            DataSourceRuntimeService dataSourceRuntimeService,
            HttpApiConnectorRegistry connectors
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.resourceService = resourceService;
        this.dataSourceRuntimeService = dataSourceRuntimeService;
        this.connectors = connectors;
    }

    public ApiResourceTestResponse test(
            UUID dataSourceId,
            UUID resourceId,
            TestApiResourceRequest request
    ) {
        long started = System.nanoTime();
        DataSource source = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已停用");
        }
        HttpApiContracts.ResourceDefinition stored = resourceService.runtimeDefinition(dataSourceId, resourceId);
        if (!stored.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API 资源已停用");
        }
        HttpApiContracts.ExecutionLimits limits = new HttpApiContracts.ExecutionLimits(
                Math.min(3, stored.limits().maxPages()),
                Math.min(10_000, stored.limits().maxRows()),
                Math.min(10L * 1024 * 1024, stored.limits().maxResponseBytes()),
                Math.min(60, stored.limits().maxDurationSeconds())
        );
        HttpApiContracts.ResourceDefinition preview = new HttpApiContracts.ResourceDefinition(
                stored.id(), stored.dataSourceId(), stored.code(), stored.name(), stored.connectorType(),
                stored.enabled(), stored.request(), stored.signing(), stored.invocationType(), stored.pagination(),
                stored.asyncJob(), stored.recordsPointer(), stored.outputFields(), limits
        );
        String[] sensitiveValues = new String[0];
        try {
            HttpApiContracts.RuntimeConnection connection = dataSourceRuntimeService.runtimeConnection(source);
            sensitiveValues = ConnectionTestDiagnosticFactory.httpSensitiveValues(connection);
            PullResult result = connectors.require(preview.connectorType()).pull(new HttpApiContracts.PullRequest(
                    connection, preview,
                    request == null || request.runtimeParameters() == null
                            ? List.of() : request.runtimeParameters()
            ));
            List<List<Object>> rows = result.rows().stream().limit(PREVIEW_ROWS).toList();
            return new ApiResourceTestResponse(
                    true, "API_RESOURCE_TEST_OK", "API 资源请求成功", elapsedMs(started),
                    result.lastHttpStatus(), result.contentType(), result.rows().size(), result.columns(), rows, null
            );
        } catch (RuntimeException exception) {
            HttpApiExecutionException api = exception instanceof HttpApiExecutionException typed
                    ? typed : new HttpApiExecutionException(
                    "API_RESOURCE_TEST_FAILED", safeMessage(exception), null, null, exception);
            var sanitized = ConnectionTestDiagnosticFactory.create(api, sensitiveValues);
            ApiTestDiagnosticResponse diagnostic = new ApiTestDiagnosticResponse(
                    api.getClass().getName(), sanitized.rawMessage(), api.httpStatus(),
                    ConnectionTestDiagnosticFactory.sanitizedText(api.responsePreview(), sensitiveValues),
                    sanitized.causes());
            LOGGER.warn(
                    "HTTP API resource test failed: dataSourceId={}, resourceId={}, code={}, httpStatus={}, elapsedMs={}\n{}",
                    dataSourceId, resourceId, api.code(), api.httpStatus(), elapsedMs(started),
                    ConnectionTestDiagnosticFactory.sanitizedStackTrace(api, sensitiveValues));
            return new ApiResourceTestResponse(
                    false, api.code(), "API 资源请求失败", elapsedMs(started), api.httpStatus(), null,
                    0, List.of(), List.of(), diagnostic
            );
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }
}
