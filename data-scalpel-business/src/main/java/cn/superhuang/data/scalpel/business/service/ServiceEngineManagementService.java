package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ServiceEngineManagementService {

    private final ServiceEngineRepository repository;
    private final DataServiceRepository dataServiceRepository;
    private final SearchEngine searchEngine;
    private final ServiceEngineClient client;
    private final ServiceEngineCredentialCipher credentialCipher;
    private final ServiceEngineDataSourceRegistrationService dataSourceRegistrationService;

    public ServiceEngineManagementService(
            ServiceEngineRepository repository,
            DataServiceRepository dataServiceRepository,
            SearchEngine searchEngine,
            ServiceEngineClient client,
            ServiceEngineCredentialCipher credentialCipher,
            ServiceEngineDataSourceRegistrationService dataSourceRegistrationService
    ) {
        this.repository = repository;
        this.dataServiceRepository = dataServiceRepository;
        this.searchEngine = searchEngine;
        this.client = client;
        this.credentialCipher = credentialCipher;
        this.dataSourceRegistrationService = dataSourceRegistrationService;
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

    @Transactional
    public ServiceEngineResponse create(CreateServiceEngineRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎编码已存在");
        }
        ServiceEngine engine = ServiceEngine.create(
                code, request.name(), request.adminUrl(), request.publicUrl(),
                credentialCipher.encrypt(request.managementToken()),
                request.enabled() == null || request.enabled(), request.description()
        );
        return ServiceEngineResponse.from(repository.saveAndFlush(engine));
    }

    @Transactional
    public ServiceEngineResponse update(UUID id, UpdateServiceEngineRequest request) {
        ServiceEngine engine = requireEngine(id);
        String managementTokenCiphertext = request.managementToken() == null || request.managementToken().isBlank()
                ? engine.getManagementTokenCiphertext()
                : credentialCipher.encrypt(request.managementToken());
        engine.update(
                request.name(), request.adminUrl(), request.publicUrl(), managementTokenCiphertext,
                request.enabled(), request.description()
        );
        return ServiceEngineResponse.from(repository.saveAndFlush(engine));
    }

    @Transactional
    public void delete(UUID id) {
        dataSourceRegistrationService.assertEngineDeletable(id);
        if (dataServiceRepository.existsByEngineId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已被数据服务使用，不能删除");
        }
        repository.delete(requireEngine(id));
    }

    public ServiceEngineTestResponse test(TestServiceEngineRequest request) {
        return testConnection(
                request.code().trim().toLowerCase(Locale.ROOT),
                ServiceEngine.normalizeAdminUrl(request.adminUrl()),
                request.managementToken()
        );
    }

    public ServiceEngineTestResponse test(UUID id, TestStoredServiceEngineRequest request) {
        ServiceEngine engine = requireEngine(id);
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
        if (!expectedCode.equals(response.code())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Service Engine Code 不一致，期望 %s，实际 %s".formatted(expectedCode, response.code())
            );
        }
        List<String> databaseTypes = response.databaseTypes() == null ? List.of() : response.databaseTypes();
        long elapsedMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        return new ServiceEngineTestResponse(response.code(), databaseTypes, elapsedMs);
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
}
