package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistrationStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Engine-aware PostGIS model choices for a GeoServer-backed service. */
@Service
public class SpatialDataServiceModelCandidateService {

    private final DataServiceRepository serviceRepository;
    private final ServiceEngineRepository engineRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ServiceEngineDataSourceRegistrationRepository registrationRepository;
    private final SearchEngine searchEngine;

    public SpatialDataServiceModelCandidateService(
            DataServiceRepository serviceRepository,
            ServiceEngineRepository engineRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            ServiceEngineDataSourceRegistrationRepository registrationRepository,
            SearchEngine searchEngine
    ) {
        this.serviceRepository = serviceRepository;
        this.engineRepository = engineRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.registrationRepository = registrationRepository;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<SpatialDataServiceModelCandidateResponse> search(
            UUID serviceId,
            SearchRequest request,
            boolean includeUnavailable
    ) {
        DataService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
        if (service.getType() != DataServiceType.SPATIAL_SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有空间服务可以选择空间模型");
        }
        var engine = engineRepository.findById(service.getEngineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎不存在"));
        if (engine.getType() != ServiceEngineType.GEOSERVER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务必须绑定 GeoServer 空间引擎");
        }

        List<ServiceEngineDataSourceRegistration> registrations =
                registrationRepository.findAllByEngineId(engine.getId());
        Map<UUID, ServiceEngineDataSourceRegistration> registrationsByDataSource = registrations.stream()
                .collect(Collectors.toMap(ServiceEngineDataSourceRegistration::getDataSourceId, Function.identity()));
        Map<UUID, DataSource> registeredDataSources = byId(dataSourceRepository.findAllById(
                registrationsByDataSource.keySet()
        ));
        Set<UUID> selectableDataSourceIds = registrations.stream()
                .filter(item -> item.getStatus() == ServiceEngineDataSourceRegistrationStatus.READY)
                .map(ServiceEngineDataSourceRegistration::getDataSourceId)
                .filter(id -> validPostGisStorage(registeredDataSources.get(id)))
                .collect(Collectors.toSet());
        Specification<DataModel> fixed = includeUnavailable
                ? Specification.unrestricted()
                : selectableModelSpecification(selectableDataSourceIds);
        Page<DataModel> page = searchEngine.search(request, DataModel.class, modelRepository, fixed);
        List<DataModel> models = page.getContent();
        Map<UUID, DataSource> dataSources = byId(dataSourceRepository.findAllById(
                models.stream().map(DataModel::getStorageDataSourceId).distinct().toList()
        ));
        Map<UUID, List<DataModelField>> fieldsByModel = new HashMap<>();
        if (!models.isEmpty()) {
            fieldRepository.findAllByModelIdInOrderByModelAndSort(
                    models.stream().map(DataModel::getId).toList()
            ).forEach(field -> fieldsByModel.computeIfAbsent(field.getModelId(), ignored -> new java.util.ArrayList<>())
                    .add(field));
        }
        List<SpatialDataServiceModelCandidateResponse> content = models.stream()
                .map(model -> response(
                        model,
                        dataSources.get(model.getStorageDataSourceId()),
                        registrationsByDataSource.get(model.getStorageDataSourceId()),
                        fieldsByModel.getOrDefault(model.getId(), List.of())
                ))
                .toList();
        return new PageResponse<>(content, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    private static SpatialDataServiceModelCandidateResponse response(
            DataModel model,
            DataSource dataSource,
            ServiceEngineDataSourceRegistration registration,
            List<DataModelField> fields
    ) {
        List<DataModelField> geometries = fields.stream()
                .filter(field -> field.getFieldType() == PlatformDataType.GEOMETRY).toList();
        List<DataModelField> primaryKeys = fields.stream().filter(DataModelField::isPrimaryKey).toList();
        DataModelField geometry = geometries.size() == 1 ? geometries.getFirst() : null;
        DataModelField primaryKey = primaryKeys.size() == 1
                && primaryKeys.getFirst().getFieldType() != PlatformDataType.GEOMETRY
                ? primaryKeys.getFirst() : null;
        String reason = unavailableReason(model, dataSource, registration, geometries, primaryKeys, geometry);
        return new SpatialDataServiceModelCandidateResponse(
                model.getId(), model.getCode(), model.getName(), model.getStatus(),
                model.getStorageDataSourceId(), dataSource == null ? null : dataSource.getCode(),
                dataSource == null ? null : dataSource.getName(), model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), geometry == null ? null : geometry.getCode(),
                geometry == null || geometry.getGeometry() == null ? null : geometry.getGeometry().kind(),
                geometry == null || geometry.getGeometry() == null ? null : geometry.getGeometry().crs().code(),
                primaryKey == null ? null : primaryKey.getCode(), reason == null, reason, model.getUpdatedAt()
        );
    }

    private static String unavailableReason(
            DataModel model,
            DataSource dataSource,
            ServiceEngineDataSourceRegistration registration,
            List<DataModelField> geometries,
            List<DataModelField> primaryKeys,
            DataModelField geometry
    ) {
        if (model.getStatus() != DataModelStatus.PUBLISHED) return "模型未发布";
        if (!validPostGisStorage(dataSource)) return "模型不是可用的 PostgreSQL/PostGIS 存储模型";
        if (registration == null) return "当前 GeoServer 尚未注册该数据源";
        if (registration.getStatus() != ServiceEngineDataSourceRegistrationStatus.READY) return "GeoServer 数据源尚未同步就绪";
        if (geometries.size() != 1) return "模型必须恰好包含一个 Geometry 字段";
        if (geometry.getGeometry() == null
                || geometry.getGeometry().dimension() != CoordinateDimension.XY
                || !"EPSG".equalsIgnoreCase(geometry.getGeometry().crs().authority())) {
            return "Geometry 字段必须使用 XY 坐标和 EPSG CRS";
        }
        if (primaryKeys.size() != 1 || primaryKeys.getFirst().getFieldType() == PlatformDataType.GEOMETRY) {
            return "模型必须恰好包含一个非 Geometry 主键字段";
        }
        if (model.getSchemaName() == null || model.getSchemaName().isBlank()) return "模型未配置物理 Schema";
        return null;
    }

    private static boolean validPostGisStorage(DataSource dataSource) {
        return dataSource != null && dataSource.isEnabled()
                && dataSource.getType() == DataSourceType.POSTGRESQL
                && dataSource.getPurposes().contains(DataSourcePurpose.STORAGE);
    }

    private static Specification<DataModel> selectableModelSpecification(Set<UUID> dataSourceIds) {
        return (root, query, builder) -> {
            if (dataSourceIds.isEmpty()) return builder.disjunction();

            var geometryCount = query.subquery(Long.class);
            var geometry = geometryCount.from(DataModelField.class);
            geometryCount.select(builder.count(geometry)).where(
                    builder.equal(geometry.get("modelId"), root.get("id")),
                    builder.equal(geometry.get("fieldType"), PlatformDataType.GEOMETRY)
            );

            var validGeometryCount = query.subquery(Long.class);
            var validGeometry = validGeometryCount.from(DataModelField.class);
            validGeometryCount.select(builder.count(validGeometry)).where(
                    builder.equal(validGeometry.get("modelId"), root.get("id")),
                    builder.equal(validGeometry.get("fieldType"), PlatformDataType.GEOMETRY),
                    builder.equal(validGeometry.get("coordinateDimension"), CoordinateDimension.XY),
                    builder.equal(builder.upper(validGeometry.get("crsAuthority")), "EPSG"),
                    builder.greaterThan(validGeometry.get("crsCode"), 0)
            );

            var primaryKeyCount = query.subquery(Long.class);
            var primaryKey = primaryKeyCount.from(DataModelField.class);
            primaryKeyCount.select(builder.count(primaryKey)).where(
                    builder.equal(primaryKey.get("modelId"), root.get("id")),
                    builder.isTrue(primaryKey.get("primaryKey"))
            );

            var nonGeometryPrimaryKeyCount = query.subquery(Long.class);
            var nonGeometryPrimaryKey = nonGeometryPrimaryKeyCount.from(DataModelField.class);
            nonGeometryPrimaryKeyCount.select(builder.count(nonGeometryPrimaryKey)).where(
                    builder.equal(nonGeometryPrimaryKey.get("modelId"), root.get("id")),
                    builder.isTrue(nonGeometryPrimaryKey.get("primaryKey")),
                    builder.notEqual(nonGeometryPrimaryKey.get("fieldType"), PlatformDataType.GEOMETRY)
            );

            return builder.and(
                    builder.equal(root.get("status"), DataModelStatus.PUBLISHED),
                    root.get("storageDataSourceId").in(dataSourceIds),
                    builder.isNotNull(root.get("schemaName")),
                    builder.notEqual(root.get("schemaName"), ""),
                    builder.equal(geometryCount, 1L),
                    builder.equal(validGeometryCount, 1L),
                    builder.equal(primaryKeyCount, 1L),
                    builder.equal(nonGeometryPrimaryKeyCount, 1L)
            );
        };
    }

    private static Map<UUID, DataSource> byId(Iterable<DataSource> values) {
        Map<UUID, DataSource> result = new HashMap<>();
        values.forEach(item -> result.put(item.getId(), item));
        return result;
    }
}
