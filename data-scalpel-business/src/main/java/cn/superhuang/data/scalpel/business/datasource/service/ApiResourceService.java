package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.ApiResource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.repository.ApiResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceResponse;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ApiResourceService {

    private final DataSourceRepository dataSourceRepository;
    private final ApiResourceRepository resourceRepository;
    private final ApiResourceConfigurationValidator validator;

    public ApiResourceService(
            DataSourceRepository dataSourceRepository,
            ApiResourceRepository resourceRepository,
            ApiResourceConfigurationValidator validator
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.resourceRepository = resourceRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<ApiResourceResponse> list(UUID dataSourceId) {
        requireApiDataSource(dataSourceId);
        return resourceRepository.findAllByDataSourceIdOrderByNameAsc(dataSourceId).stream()
                .map(ApiResourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApiResourceResponse get(UUID dataSourceId, UUID resourceId) {
        requireApiDataSource(dataSourceId);
        return ApiResourceResponse.from(requireResource(dataSourceId, resourceId));
    }

    @Transactional
    public ApiResourceResponse create(UUID dataSourceId, CreateApiResourceRequest request) {
        DataSource source = requireApiDataSource(dataSourceId);
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (resourceRepository.existsByDataSourceIdAndCode(dataSourceId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API 资源编码已存在");
        }
        HttpApiContracts.ResourceDefinition definition = definition(
                null, dataSourceId, code, request.name(), request.connectorType(),
                request.enabled() == null || request.enabled(), request.request(), request.signing(),
                request.invocationType(), request.pagination(), request.asyncJob(), request.recordsPointer(),
                request.outputFields(), request.limits());
        validator.validate(source, definition);
        ApiResource resource = ApiResource.create(
                dataSourceId, code, request.name(), definition.connectorType(), definition.enabled(),
                HttpApiConfigurationCodec.writeResource(definition));
        return ApiResourceResponse.from(resourceRepository.saveAndFlush(resource));
    }

    @Transactional
    public ApiResourceResponse update(
            UUID dataSourceId,
            UUID resourceId,
            UpdateApiResourceRequest request
    ) {
        DataSource source = requireApiDataSource(dataSourceId);
        ApiResource resource = requireResource(dataSourceId, resourceId);
        HttpApiContracts.ResourceDefinition definition = definition(
                resourceId, dataSourceId, resource.getCode(), request.name(), request.connectorType(),
                request.enabled(), request.request(), request.signing(), request.invocationType(),
                request.pagination(), request.asyncJob(), request.recordsPointer(), request.outputFields(),
                request.limits());
        validator.validate(source, definition);
        resource.update(request.name(), definition.connectorType(), definition.enabled(),
                HttpApiConfigurationCodec.writeResource(definition));
        return ApiResourceResponse.from(resourceRepository.saveAndFlush(resource));
    }

    @Transactional
    public void delete(UUID dataSourceId, UUID resourceId) {
        requireApiDataSource(dataSourceId);
        resourceRepository.delete(requireResource(dataSourceId, resourceId));
    }

    @Transactional(readOnly = true)
    public HttpApiContracts.ResourceDefinition runtimeDefinition(UUID dataSourceId, UUID resourceId) {
        ApiResource resource = requireResource(dataSourceId, resourceId);
        HttpApiContracts.ResourceDefinition stored = HttpApiConfigurationCodec.readResource(resource.getDefinitionJson());
        return definition(
                resource.getId(), resource.getDataSourceId(), resource.getCode(), resource.getName(),
                resource.getConnectorType(), resource.isEnabled(), stored.request(), stored.signing(),
                stored.invocationType(), stored.pagination(), stored.asyncJob(), stored.recordsPointer(),
                stored.outputFields(), stored.limits());
    }

    private DataSource requireApiDataSource(UUID id) {
        DataSource source = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (source.getType().connectionKind() != DataSourceConnectionKind.HTTP_API) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前数据源不是 HTTP API");
        }
        return source;
    }

    private ApiResource requireResource(UUID dataSourceId, UUID resourceId) {
        return resourceRepository.findByIdAndDataSourceId(resourceId, dataSourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API 资源不存在"));
    }

    private static HttpApiContracts.ResourceDefinition definition(
            UUID id,
            UUID dataSourceId,
            String code,
            String name,
            String connectorType,
            boolean enabled,
            HttpApiContracts.RequestTemplate request,
            HttpApiContracts.SigningConfiguration signing,
            HttpApiContracts.InvocationType invocationType,
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.AsyncJobConfiguration asyncJob,
            String recordsPointer,
            List<HttpApiContracts.OutputField> outputFields,
            HttpApiContracts.ExecutionLimits limits
    ) {
        return new HttpApiContracts.ResourceDefinition(
                id, dataSourceId, code.trim().toLowerCase(Locale.ROOT), name.trim(),
                connectorType == null || connectorType.isBlank()
                        ? HttpApiContracts.GENERIC_CONNECTOR : connectorType.trim(),
                enabled, request, signing == null ? HttpApiContracts.SigningConfiguration.none() : signing,
                invocationType, pagination == null ? new HttpApiContracts.NoPagination() : pagination,
                asyncJob, recordsPointer.trim(), outputFields, limits
        );
    }
}
