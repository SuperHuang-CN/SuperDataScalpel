package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineRequest;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class ServiceEngineManagementService {

    private final ServiceEngineRepository repository;
    private final DataServiceRepository dataServiceRepository;
    private final SearchEngine searchEngine;
    private final ServiceEngineClient client;
    private final ServiceEngineDataSourceRegistrationService dataSourceRegistrationService;

    public ServiceEngineManagementService(
            ServiceEngineRepository repository,
            DataServiceRepository dataServiceRepository,
            SearchEngine searchEngine,
            ServiceEngineClient client,
            ServiceEngineDataSourceRegistrationService dataSourceRegistrationService
    ) {
        this.repository = repository;
        this.dataServiceRepository = dataServiceRepository;
        this.searchEngine = searchEngine;
        this.client = client;
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
                request.enabled() == null || request.enabled(), request.description()
        );
        return ServiceEngineResponse.from(repository.saveAndFlush(engine));
    }

    @Transactional
    public ServiceEngineResponse update(UUID id, UpdateServiceEngineRequest request) {
        ServiceEngine engine = requireEngine(id);
        engine.update(request.name(), request.adminUrl(), request.publicUrl(), request.enabled(), request.description());
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

    public ServiceEngineTestResponse test(UUID id) {
        ServiceEngine engine = requireEngine(id);
        ServiceEngineInfoResponse response;
        try {
            response = client.info(engine);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务引擎不可访问：" + safeMessage(exception), exception);
        }
        return new ServiceEngineTestResponse(response.code(), response.databaseTypes());
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
