package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestStoredServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineTestResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ServiceEngineManagementService {

    private final ServiceEngineRepository repository;
    private final DataServiceRepository dataServiceRepository;
    private final SearchEngine searchEngine;
    private final ServiceEngineClient client;
    private final GeoServerClient geoServerClient;
    private final ServiceEngineCredentialCipher credentialCipher;
    private final ServiceEngineDataSourceRegistrationService dataSourceRegistrationService;
    private final ServiceEngineAccessPolicyService accessPolicyService;
    private final TransactionTemplate transactionTemplate;

    public ServiceEngineManagementService(
            ServiceEngineRepository repository,
            DataServiceRepository dataServiceRepository,
            SearchEngine searchEngine,
            ServiceEngineClient client,
            GeoServerClient geoServerClient,
            ServiceEngineCredentialCipher credentialCipher,
            ServiceEngineDataSourceRegistrationService dataSourceRegistrationService,
            ServiceEngineAccessPolicyService accessPolicyService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.dataServiceRepository = dataServiceRepository;
        this.searchEngine = searchEngine;
        this.client = client;
        this.geoServerClient = geoServerClient;
        this.credentialCipher = credentialCipher;
        this.dataSourceRegistrationService = dataSourceRegistrationService;
        this.accessPolicyService = accessPolicyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceEngineResponse> search(SearchRequest request) {
        Page<ServiceEngine> result = searchEngine.search(request, ServiceEngine.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(ServiceEngineResponse::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ServiceEngineResponse get(UUID id) {
        return ServiceEngineResponse.from(requireEngine(id));
    }

    public ServiceEngineResponse create(CreateServiceEngineRequest request) {
        if (request.type() == ServiceEngineType.GEOSERVER) {
            return createGeoServer(request);
        }
        requireText(request.managementToken(), "Management Token");
        String adminUrl = ServiceEngine.normalizeAdminUrl(request.adminUrl());
        ServiceEngineTestResponse discovery = testConnection(null, adminUrl, request.managementToken());
        return requireTransactionResult(transactionTemplate.execute(status -> {
            if (repository.existsByCode(discovery.code())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎编码已存在");
            }
            ServiceEngine engine = ServiceEngine.create(
                    discovery.code(), request.name(), adminUrl, request.runtimeUrl(),
                    credentialCipher.encrypt(request.managementToken()),
                    request.enabled() == null || request.enabled(), request.description()
            );
            return ServiceEngineResponse.from(repository.saveAndFlush(engine));
        }));
    }

    private ServiceEngineResponse createGeoServer(CreateServiceEngineRequest request) {
        String code = ServiceEngine.normalizeCode(requireText(request.code(), "GeoServer Engine Code"));
        requireText(request.geoServerUsername(), "GeoServer 用户名");
        requireText(request.geoServerPassword(), "GeoServer 密码");
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎编码已存在");
        }
        GeoServerClient.Discovery discovery = geoServerClient.discover(
                code, request.adminUrl(), request.runtimeUrl(), request.geoServerUsername(),
                request.geoServerPassword(), request.geoServerWorkspace(), true
        );
        return requireTransactionResult(transactionTemplate.execute(status -> {
            if (repository.existsByCode(discovery.code())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎编码已存在");
            }
            ServiceEngine engine = ServiceEngine.createGeoServer(
                    discovery.code(), request.name(), discovery.adminUrl(), discovery.runtimeUrl(),
                    request.geoServerUsername(), credentialCipher.encrypt(request.geoServerPassword()),
                    discovery.workspace(), request.enabled() == null || request.enabled(), request.description()
            );
            return ServiceEngineResponse.from(repository.saveAndFlush(engine));
        }));
    }

    public ServiceEngineResponse update(UUID id, UpdateServiceEngineRequest request) {
        ServiceEngine stored = requireEngine(id);
        if (stored.getType() == ServiceEngineType.GEOSERVER) {
            return updateGeoServer(id, request);
        }
        UpdatePreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEngine(id);
            String adminUrl = ServiceEngine.normalizeAdminUrl(request.adminUrl());
            String runtimeUrl = ServiceEngine.normalizeRuntimeUrl(request.runtimeUrl());
            boolean tokenChanged = request.managementToken() != null && !request.managementToken().isBlank();
            String managementToken = tokenChanged
                    ? request.managementToken().trim()
                    : credentialCipher.decrypt(engine.getManagementTokenCiphertext());
            String managementTokenCiphertext = tokenChanged
                    ? credentialCipher.encrypt(managementToken)
                    : engine.getManagementTokenCiphertext();
            boolean identityChanged = !engine.getAdminUrl().equals(adminUrl)
                    || !engine.getRuntimeUrl().equals(runtimeUrl)
                    || tokenChanged;
            return new UpdatePreparation(
                    engine.getCode(), adminUrl, runtimeUrl, managementToken, managementTokenCiphertext, identityChanged
            );
        }));
        if (preparation.identityChanged()) {
            testConnection(preparation.code(), preparation.adminUrl(), preparation.managementToken());
        }
        return requireTransactionResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEngine(id);
            engine.update(
                    request.name(), preparation.adminUrl(), preparation.runtimeUrl(),
                    preparation.managementTokenCiphertext(), request.enabled(), request.description()
            );
            ServiceEngineResponse response = ServiceEngineResponse.from(repository.saveAndFlush(engine));
            if (preparation.identityChanged()) accessPolicyService.markOutdated(id);
            return response;
        }));
    }

    private ServiceEngineResponse updateGeoServer(UUID id, UpdateServiceEngineRequest request) {
        GeoServerUpdatePreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEngine(id);
            String adminUrl = ServiceEngine.normalizeAdminUrl(request.adminUrl());
            String runtimeUrl = ServiceEngine.normalizeRuntimeUrl(request.runtimeUrl());
            String workspace = ServiceEngine.normalizeWorkspace(request.geoServerWorkspace());
            String username = request.geoServerUsername() == null || request.geoServerUsername().isBlank()
                    ? engine.getGeoServerUsername()
                    : request.geoServerUsername().trim();
            boolean passwordChanged = request.geoServerPassword() != null && !request.geoServerPassword().isBlank();
            String password = passwordChanged
                    ? request.geoServerPassword().trim()
                    : credentialCipher.decrypt(engine.getGeoServerPasswordCiphertext());
            boolean managedIdentityChanged = !engine.getAdminUrl().equals(adminUrl)
                    || !Objects.equals(engine.getGeoServerWorkspace(), workspace);
            if (managedIdentityChanged
                    && (dataSourceRegistrationService.hasRegistrations(id)
                    || dataServiceRepository.existsByEngineId(id))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "GeoServer 已有关联数据源或数据服务，不能修改管理地址或 Workspace"
                );
            }
            return new GeoServerUpdatePreparation(
                    engine.getCode(), adminUrl, runtimeUrl, username, password,
                    passwordChanged ? credentialCipher.encrypt(password) : engine.getGeoServerPasswordCiphertext(),
                    workspace
            );
        }));
        GeoServerClient.Discovery discovery = geoServerClient.discover(
                preparation.code(), preparation.adminUrl(), preparation.runtimeUrl(),
                preparation.username(), preparation.password(), preparation.workspace(), true
        );
        return requireTransactionResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEngine(id);
            engine.update(
                    request.name(), discovery.adminUrl(), discovery.runtimeUrl(), null,
                    preparation.username(), preparation.passwordCiphertext(), discovery.workspace(),
                    request.enabled(), request.description()
            );
            return ServiceEngineResponse.from(repository.saveAndFlush(engine));
        }));
    }

    @Transactional
    public void delete(UUID id) {
        dataSourceRegistrationService.assertEngineDeletable(id);
        if (dataServiceRepository.existsByEngineId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已被数据服务使用，不能删除");
        }
        accessPolicyService.deleteByEngineId(id);
        repository.delete(requireEngine(id));
    }

    public ServiceEngineTestResponse test(TestServiceEngineRequest request) {
        if (request.type() == ServiceEngineType.GEOSERVER) {
            long startedAt = System.nanoTime();
            GeoServerClient.Discovery discovery = geoServerClient.discover(
                    requireText(request.code(), "GeoServer Engine Code"), request.adminUrl(), request.runtimeUrl(),
                    requireText(request.geoServerUsername(), "GeoServer 用户名"),
                    requireText(request.geoServerPassword(), "GeoServer 密码"),
                    request.geoServerWorkspace(), false
            );
            return geoServerTestResponse(discovery, startedAt);
        }
        return testConnection(
                null,
                ServiceEngine.normalizeAdminUrl(request.adminUrl()),
                request.managementToken()
        );
    }

    public ServiceEngineTestResponse test(UUID id, TestStoredServiceEngineRequest request) {
        ServiceEngine engine = requireEngine(id);
        if (engine.getType() == ServiceEngineType.GEOSERVER) {
            String adminUrl = request == null || request.adminUrl() == null || request.adminUrl().isBlank()
                    ? engine.getAdminUrl() : request.adminUrl();
            String runtimeUrl = request == null || request.runtimeUrl() == null || request.runtimeUrl().isBlank()
                    ? engine.getRuntimeUrl() : request.runtimeUrl();
            String username = request == null || request.geoServerUsername() == null || request.geoServerUsername().isBlank()
                    ? engine.getGeoServerUsername() : request.geoServerUsername();
            String password = request == null || request.geoServerPassword() == null || request.geoServerPassword().isBlank()
                    ? credentialCipher.decrypt(engine.getGeoServerPasswordCiphertext()) : request.geoServerPassword();
            String workspace = request == null || request.geoServerWorkspace() == null || request.geoServerWorkspace().isBlank()
                    ? engine.getGeoServerWorkspace() : request.geoServerWorkspace();
            long startedAt = System.nanoTime();
            return geoServerTestResponse(geoServerClient.discover(
                    engine.getCode(), adminUrl, runtimeUrl, username, password, workspace, false
            ), startedAt);
        }
        String adminUrl = request == null || request.adminUrl() == null || request.adminUrl().isBlank()
                ? engine.getAdminUrl()
                : ServiceEngine.normalizeAdminUrl(request.adminUrl());
        String managementToken = request == null
                || request.managementToken() == null
                || request.managementToken().isBlank()
                ? credentialCipher.decrypt(engine.getManagementTokenCiphertext())
                : request.managementToken();
        return testConnection(engine.getCode(), adminUrl, managementToken);
    }

    private ServiceEngineTestResponse testConnection(String expectedCode, String adminUrl, String managementToken) {
        long startedAt = System.nanoTime();
        ServiceEngineInfoResponse response;
        try {
            response = client.info(adminUrl, managementToken);
        } catch (RuntimeException exception) {
            throw testFailure(exception);
        }
        if (response == null || response.code() == null || response.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service Engine 返回的身份信息不完整");
        }
        String actualCode;
        try {
            actualCode = ServiceEngine.normalizeCode(response.code());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Service Engine 返回的 Code 不符合编码规范",
                    exception
            );
        }
        if (expectedCode != null && !expectedCode.equals(actualCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Service Engine Code 不一致，期望 %s，实际 %s".formatted(expectedCode, actualCode)
            );
        }
        List<String> databaseTypes = response.databaseTypes() == null ? List.of() : response.databaseTypes();
        long elapsedMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        return new ServiceEngineTestResponse(
                ServiceEngineType.DATASCALPEL, actualCode, null, databaseTypes,
                List.of("STANDARD_TABLE", "SQL_QUERY", "SCRIPT_API"), adminUrl, null, elapsedMs
        );
    }

    private static ServiceEngineTestResponse geoServerTestResponse(
            GeoServerClient.Discovery discovery,
            long startedAt
    ) {
        return new ServiceEngineTestResponse(
                ServiceEngineType.GEOSERVER, discovery.code(), discovery.version(), discovery.databaseTypes(),
                discovery.capabilities(), discovery.adminUrl(), discovery.runtimeUrl(),
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000)
        );
    }

    private static ResponseStatusException testFailure(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException
                && (responseException.getStatusCode().value() == 401
                || responseException.getStatusCode().value() == 403)) {
            return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Service Engine 拒绝访问，请检查 Management Token",
                    exception
            );
        }
        if (exception instanceof RestClientResponseException responseException) {
            return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Service Engine 返回异常状态：HTTP " + responseException.getStatusCode().value(),
                    exception
            );
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "服务引擎不可访问：" + safeMessage(exception),
                exception
        );
    }

    private ServiceEngine requireEngine(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务引擎不存在"));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "远程调用失败" : message.substring(0, Math.min(300, message.length()));
    }

    private static <T> T requireTransactionResult(T value) {
        return Objects.requireNonNull(value, "Transaction result is required");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private record UpdatePreparation(
            String code,
            String adminUrl,
            String runtimeUrl,
            String managementToken,
            String managementTokenCiphertext,
            boolean identityChanged
    ) {
    }

    private record GeoServerUpdatePreparation(
            String code,
            String adminUrl,
            String runtimeUrl,
            String username,
            String password,
            String passwordCiphertext,
            String workspace
    ) {
    }
}
