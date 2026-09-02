package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceModelReferenceRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelRole;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves ordered data-service model references without issuing one query per model. */
@Service
public class DataServiceRelatedModelService {

    private final DataServiceRepository serviceRepository;
    private final StandardDataServiceDefinitionRepository standardDefinitionRepository;
    private final SqlDataServiceModelReferenceRepository sqlModelReferenceRepository;
    private final SpatialDataServiceDefinitionRepository spatialDefinitionRepository;
    private final DataModelRepository modelRepository;
    private final DirectoryRepository directoryRepository;
    private final ModelWarehouseLayerRepository warehouseLayerRepository;
    private final DataSourceRepository dataSourceRepository;

    public DataServiceRelatedModelService(
            DataServiceRepository serviceRepository,
            StandardDataServiceDefinitionRepository standardDefinitionRepository,
            SqlDataServiceModelReferenceRepository sqlModelReferenceRepository,
            SpatialDataServiceDefinitionRepository spatialDefinitionRepository,
            DataModelRepository modelRepository,
            DirectoryRepository directoryRepository,
            ModelWarehouseLayerRepository warehouseLayerRepository,
            DataSourceRepository dataSourceRepository
    ) {
        this.serviceRepository = serviceRepository;
        this.standardDefinitionRepository = standardDefinitionRepository;
        this.sqlModelReferenceRepository = sqlModelReferenceRepository;
        this.spatialDefinitionRepository = spatialDefinitionRepository;
        this.modelRepository = modelRepository;
        this.directoryRepository = directoryRepository;
        this.warehouseLayerRepository = warehouseLayerRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    @Transactional(readOnly = true)
    public List<DataServiceRelatedModelResponse> get(UUID serviceId) {
        DataService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
        List<ModelReference> references = references(service);
        if (references.isEmpty()) return List.of();

        Map<UUID, DataModel> models = byId(modelRepository.findAllById(
                references.stream().map(ModelReference::modelId).distinct().toList()
        ));
        Collection<DataModel> resolvedModels = models.values();
        Map<UUID, Directory> directories = byId(directoryRepository.findAllById(
                nonNullIds(resolvedModels.stream().map(DataModel::getDirectoryId).toList())
        ));
        Map<UUID, ModelWarehouseLayer> layers = byId(warehouseLayerRepository.findAllById(
                nonNullIds(resolvedModels.stream().map(DataModel::getWarehouseLayerId).toList())
        ));
        Map<UUID, DataSource> dataSources = byId(dataSourceRepository.findAllById(
                resolvedModels.stream().map(DataModel::getStorageDataSourceId).distinct().toList()
        ));

        return references.stream().map(reference -> response(
                reference,
                models.get(reference.modelId()),
                directories,
                layers,
                dataSources
        )).toList();
    }

    private List<ModelReference> references(DataService service) {
        if (service.getType() == DataServiceType.STANDARD_TABLE) {
            return standardDefinitionRepository.findByDataServiceId(service.getId())
                    .map(definition -> List.of(new ModelReference(
                            definition.getModelId(), DataServiceRelatedModelRole.PRIMARY, 1
                    )))
                    .orElseGet(List::of);
        }
        if (service.getType() == DataServiceType.SQL_QUERY) {
            return sqlModelReferenceRepository.findAllByDataServiceIdOrderBySortOrderAsc(service.getId()).stream()
                    .map(reference -> new ModelReference(
                            reference.getModelId(), DataServiceRelatedModelRole.REFERENCE,
                            reference.getSortOrder() + 1
                    ))
                    .toList();
        }
        if (service.getType() == DataServiceType.SPATIAL_SERVICE) {
            return spatialDefinitionRepository.findByDataServiceId(service.getId())
                    .map(definition -> List.of(new ModelReference(
                            definition.getModelId(), DataServiceRelatedModelRole.PRIMARY, 1
                    )))
                    .orElseGet(List::of);
        }
        return List.of();
    }

    private static DataServiceRelatedModelResponse response(
            ModelReference reference,
            DataModel model,
            Map<UUID, Directory> directories,
            Map<UUID, ModelWarehouseLayer> layers,
            Map<UUID, DataSource> dataSources
    ) {
        if (model == null) {
            return new DataServiceRelatedModelResponse(
                    reference.modelId(), reference.role(), reference.order(), false,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null
            );
        }
        Directory directory = directories.get(model.getDirectoryId());
        ModelWarehouseLayer layer = layers.get(model.getWarehouseLayerId());
        DataSource dataSource = dataSources.get(model.getStorageDataSourceId());
        return new DataServiceRelatedModelResponse(
                reference.modelId(), reference.role(), reference.order(), true,
                model.getCode(), model.getName(), model.getStatus(),
                model.getDirectoryId(), directory == null ? null : directory.getName(),
                model.getWarehouseLayerId(), layer == null ? null : layer.getCode(),
                layer == null ? null : layer.getName(),
                model.getStorageDataSourceId(), dataSource == null ? null : dataSource.getCode(),
                dataSource == null ? null : dataSource.getName(),
                model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName(),
                model.getSchemaVersion(), model.getUpdatedAt()
        );
    }

    private static <T extends cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity> Map<UUID, T> byId(
            Iterable<T> entities
    ) {
        Map<UUID, T> result = new HashMap<>();
        entities.forEach(entity -> result.put(entity.getId(), entity));
        return result;
    }

    private static Collection<UUID> nonNullIds(Collection<UUID> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private record ModelReference(UUID modelId, DataServiceRelatedModelRole role, int order) {
    }
}
