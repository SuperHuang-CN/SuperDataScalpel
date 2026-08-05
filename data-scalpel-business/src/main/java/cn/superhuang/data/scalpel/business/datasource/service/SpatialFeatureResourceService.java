package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.domain.SpatialFeatureResource;
import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.SpatialFeatureResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialCatalogEntryResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialFeatureResourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialFeaturePreviewResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Coordinates durable spatial resources while all external calls stay outside database transactions. */
@Service
public class SpatialFeatureResourceService {

    private final DataSourceRepository dataSourceRepository;
    private final SpatialFeatureResourceRepository resourceRepository;
    private final DataSourceRuntimeService dataSourceRuntimeService;
    private final SpatialServiceClient spatialServiceClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public SpatialFeatureResourceService(
            DataSourceRepository dataSourceRepository,
            SpatialFeatureResourceRepository resourceRepository,
            DataSourceRuntimeService dataSourceRuntimeService,
            SpatialServiceClient spatialServiceClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.resourceRepository = resourceRepository;
        this.dataSourceRuntimeService = dataSourceRuntimeService;
        this.spatialServiceClient = spatialServiceClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<SpatialFeatureResourceResponse> list(UUID dataSourceId) {
        requireSpatialDataSource(dataSourceId);
        return resourceRepository.findAllByDataSourceIdOrderByNameAsc(dataSourceId).stream()
                .map(resource -> response(resource)).toList();
    }

    @Transactional(readOnly = true)
    public SpatialFeatureResourceResponse get(UUID dataSourceId, UUID resourceId) {
        requireSpatialDataSource(dataSourceId);
        return response(requireResource(dataSourceId, resourceId));
    }

    @Transactional(readOnly = true)
    public List<SpatialCatalogEntryResponse> discover(UUID dataSourceId, String parent) {
        DataSource source = requireSpatialDataSource(dataSourceId);
        if (!source.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已停用");
        return spatialServiceClient.discover(protocol(source), dataSourceRuntimeService.runtimeConnection(source), parent);
    }

    public SpatialFeatureResourceResponse create(UUID dataSourceId, CreateSpatialFeatureResourceRequest request) {
        DataSource source = requireSpatialDataSource(dataSourceId);
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (resourceRepository.existsByDataSourceIdAndCode(dataSourceId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间要素资源编码已存在");
        }
        SpatialFeatureDefinition definition = spatialServiceClient.describe(
                protocol(source), dataSourceRuntimeService.runtimeConnection(source),
                request.remoteIdentifier(), request.outputEpsgCode());
        return transactionTemplate.execute(status -> {
            SpatialFeatureResource resource = SpatialFeatureResource.create(
                    dataSourceId, code, request.name(), protocol(source), request.remoteIdentifier(), true,
                    writeDefinition(definition));
            return response(resourceRepository.saveAndFlush(resource));
        });
    }

    @Transactional
    public SpatialFeatureResourceResponse update(
            UUID dataSourceId, UUID resourceId, UpdateSpatialFeatureResourceRequest request
    ) {
        requireSpatialDataSource(dataSourceId);
        SpatialFeatureResource resource = requireResource(dataSourceId, resourceId);
        resource.update(request.name(), request.enabled(), resource.getDefinitionJson());
        return response(resourceRepository.saveAndFlush(resource));
    }

    public SpatialFeatureResourceResponse refresh(UUID dataSourceId, UUID resourceId) {
        DataSource source = requireSpatialDataSource(dataSourceId);
        SpatialFeatureResource current = requireResource(dataSourceId, resourceId);
        SpatialFeatureDefinition prior = readDefinition(current);
        SpatialFeatureDefinition refreshed = spatialServiceClient.describe(
                protocol(source), dataSourceRuntimeService.runtimeConnection(source), current.getRemoteIdentifier(), prior.epsgCode());
        return transactionTemplate.execute(status -> {
            SpatialFeatureResource resource = requireResource(dataSourceId, resourceId);
            resource.update(resource.getName(), resource.isEnabled(), writeDefinition(refreshed));
            return response(resourceRepository.saveAndFlush(resource));
        });
    }

    @Transactional(readOnly = true)
    public SpatialFeaturePreviewResponse preview(UUID dataSourceId, UUID resourceId, int limit) {
        DataSource source = requireSpatialDataSource(dataSourceId);
        if (!source.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已停用");
        SpatialFeatureResource resource = requireResource(dataSourceId, resourceId);
        if (!resource.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "空间要素资源已停用");
        SpatialFeatureDefinition definition = readDefinition(resource);
        SpatialServiceClient.SpatialPreview preview = spatialServiceClient.preview(
                protocol(source), dataSourceRuntimeService.runtimeConnection(source), definition, limit);
        return new SpatialFeaturePreviewResponse(
                definition.columns().stream().filter(column -> column.fieldType() != PlatformDataType.GEOMETRY).toList(),
                preview.rows(), limit, preview.truncated());
    }

    @Transactional
    public void delete(UUID dataSourceId, UUID resourceId) {
        requireSpatialDataSource(dataSourceId);
        resourceRepository.delete(requireResource(dataSourceId, resourceId));
    }

    @Transactional(readOnly = true)
    public boolean hasResources(UUID dataSourceId) {
        return resourceRepository.existsByDataSourceId(dataSourceId);
    }

    @Transactional(readOnly = true)
    public cn.superhuang.data.scalpel.contract.task.SpatialServiceResourceDefinition runtimeDefinition(
            UUID dataSourceId,
            UUID resourceId
    ) {
        DataSource source = requireSpatialDataSource(dataSourceId);
        SpatialFeatureResource resource = requireResource(dataSourceId, resourceId);
        SpatialFeatureDefinition definition = readDefinition(resource);
        return new cn.superhuang.data.scalpel.contract.task.SpatialServiceResourceDefinition(
                resource.getId(), resource.getCode(), resource.getName(), resource.isEnabled(),
                source.getType() == DataSourceType.ARCGIS_REST
                        ? cn.superhuang.data.scalpel.contract.task.SpatialServiceProtocol.ARCGIS_REST
                        : cn.superhuang.data.scalpel.contract.task.SpatialServiceProtocol.WFS,
                resource.getRemoteIdentifier(), definition.geometryFieldName(), definition.epsgCode(),
                definition.objectIdFieldName(), definition.wfsVersion(), definition.outputFormat(),
                definition.axisOrder(), definition.columns()
        );
    }

    private DataSource requireSpatialDataSource(UUID id) {
        DataSource source = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        if (source.getType() != DataSourceType.ARCGIS_REST && source.getType() != DataSourceType.WFS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前数据源不是 ArcGIS REST 或 WFS");
        }
        return source;
    }

    private SpatialFeatureResource requireResource(UUID dataSourceId, UUID resourceId) {
        return resourceRepository.findByIdAndDataSourceId(resourceId, dataSourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "空间要素资源不存在"));
    }

    private SpatialFeatureResourceResponse response(SpatialFeatureResource resource) {
        return SpatialFeatureResourceResponse.from(resource, readDefinition(resource));
    }

    private SpatialFeatureDefinition readDefinition(SpatialFeatureResource resource) {
        try { return objectMapper.readValue(resource.getDefinitionJson(), SpatialFeatureDefinition.class); }
        catch (RuntimeException exception) { throw new IllegalStateException("空间要素资源定义损坏", exception); }
    }

    private String writeDefinition(SpatialFeatureDefinition definition) {
        try { return objectMapper.writeValueAsString(definition); }
        catch (RuntimeException exception) { throw new IllegalStateException("无法保存空间要素资源定义", exception); }
    }

    private static SpatialServiceProtocol protocol(DataSource source) {
        return source.getType() == DataSourceType.ARCGIS_REST
                ? SpatialServiceProtocol.ARCGIS_REST : SpatialServiceProtocol.WFS;
    }
}
