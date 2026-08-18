package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistrationStatus;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.web.response.StandardDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds scalable, Engine-aware model choices for a standard-table data service. */
@Service
public class StandardDataServiceModelCandidateService {

    private final DataServiceRepository serviceRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ServiceEngineDataSourceRegistrationRepository registrationRepository;
    private final DirectoryRepository directoryRepository;
    private final ModelWarehouseLayerRepository warehouseLayerRepository;
    private final SearchEngine searchEngine;

    public StandardDataServiceModelCandidateService(
            DataServiceRepository serviceRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            ServiceEngineDataSourceRegistrationRepository registrationRepository,
            DirectoryRepository directoryRepository,
            ModelWarehouseLayerRepository warehouseLayerRepository,
            SearchEngine searchEngine
    ) {
        this.serviceRepository = serviceRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.registrationRepository = registrationRepository;
        this.directoryRepository = directoryRepository;
        this.warehouseLayerRepository = warehouseLayerRepository;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<StandardDataServiceModelCandidateResponse> search(
            UUID serviceId,
            SearchRequest request,
            boolean includeUnavailable
    ) {
        DataService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
        if (service.getType() != DataServiceType.STANDARD_TABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有标准单表服务可以选择发布模型");
        }

        List<ServiceEngineDataSourceRegistration> registrations =
                registrationRepository.findAllByEngineId(service.getEngineId());
        Map<UUID, ServiceEngineDataSourceRegistration> registrationsByDataSource = registrations.stream()
                .collect(Collectors.toMap(
                        ServiceEngineDataSourceRegistration::getDataSourceId,
                        Function.identity()
                ));
        Map<UUID, DataSource> registeredDataSources = byId(dataSourceRepository.findAllById(
                registrationsByDataSource.keySet()
        ));
        Set<UUID> selectableDataSourceIds = registrations.stream()
                .filter(registration -> registration.getStatus() == ServiceEngineDataSourceRegistrationStatus.READY)
                .map(ServiceEngineDataSourceRegistration::getDataSourceId)
                .filter(dataSourceId -> validStorageDataSource(registeredDataSources.get(dataSourceId)))
                .collect(Collectors.toSet());

        Specification<DataModel> base = includeUnavailable
                ? Specification.unrestricted()
                : selectableSpecification(selectableDataSourceIds);
        Page<DataModel> page = searchEngine.search(request, DataModel.class, modelRepository, base);
        List<DataModel> models = page.getContent();
        Map<UUID, DataSource> dataSources = byId(dataSourceRepository.findAllById(
                models.stream().map(DataModel::getStorageDataSourceId).distinct().toList()
        ));
        Map<UUID, Directory> directories = byId(directoryRepository.findAllById(
                nonNullIds(models.stream().map(DataModel::getDirectoryId).toList())
        ));
        Map<UUID, ModelWarehouseLayer> layers = byId(warehouseLayerRepository.findAllById(
                nonNullIds(models.stream().map(DataModel::getWarehouseLayerId).toList())
        ));
        Map<UUID, Long> fieldCounts = models.isEmpty()
                ? Map.of()
                : fieldRepository.countByModelIdIn(
                        models.stream().map(DataModel::getId).toList()
                ).stream().collect(Collectors.toMap(
                        DataModelFieldRepository.ModelFieldCount::modelId,
                        DataModelFieldRepository.ModelFieldCount::fieldCount
                ));

        return new PageResponse<>(
                models.stream().map(model -> response(
                        model,
                        directories.get(model.getDirectoryId()),
                        layers.get(model.getWarehouseLayerId()),
                        dataSources.get(model.getStorageDataSourceId()),
                        registrationsByDataSource.get(model.getStorageDataSourceId()),
                        fieldCounts.getOrDefault(model.getId(), 0L)
                )).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    private static Specification<DataModel> selectableSpecification(Set<UUID> dataSourceIds) {
        return (root, query, builder) -> {
            if (dataSourceIds.isEmpty()) return builder.disjunction();
            Subquery<Integer> fields = query.subquery(Integer.class);
            Root<DataModelField> field = fields.from(DataModelField.class);
            fields.select(builder.literal(1));
            fields.where(builder.equal(field.get("modelId"), root.get("id")));
            return builder.and(
                    builder.equal(root.get("status"), DataModelStatus.PUBLISHED),
                    root.get("storageDataSourceId").in(dataSourceIds),
                    builder.exists(fields)
            );
        };
    }

    private static StandardDataServiceModelCandidateResponse response(
            DataModel model,
            Directory directory,
            ModelWarehouseLayer layer,
            DataSource dataSource,
            ServiceEngineDataSourceRegistration registration,
            long fieldCount
    ) {
        String unavailableReason = unavailableReason(model, dataSource, registration, fieldCount);
        return new StandardDataServiceModelCandidateResponse(
                model.getId(), model.getCode(), model.getName(), model.getStatus(),
                model.getDirectoryId(), directory == null ? null : directory.getName(),
                model.getWarehouseLayerId(), layer == null ? null : layer.getCode(), layer == null ? null : layer.getName(),
                model.getStorageDataSourceId(), dataSource == null ? null : dataSource.getCode(),
                dataSource == null ? null : dataSource.getName(),
                model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName(),
                model.getSchemaVersion(), fieldCount, unavailableReason == null, unavailableReason,
                model.getUpdatedAt()
        );
    }

    private static String unavailableReason(
            DataModel model,
            DataSource dataSource,
            ServiceEngineDataSourceRegistration registration,
            long fieldCount
    ) {
        if (model.getStatus() != DataModelStatus.PUBLISHED) return "模型未发布";
        if (fieldCount == 0) return "模型尚未定义字段";
        if (!validStorageDataSource(dataSource)) return "模型存储数据源不可用";
        if (registration == null) return "当前 Engine 尚未注册该数据源";
        if (registration.getStatus() != ServiceEngineDataSourceRegistrationStatus.READY) {
            return "当前 Engine 的数据源注册尚未就绪";
        }
        return null;
    }

    private static boolean validStorageDataSource(DataSource dataSource) {
        return dataSource != null
                && dataSource.isEnabled()
                && dataSource.getType().isJdbc()
                && dataSource.getPurposes().contains(DataSourcePurpose.STORAGE);
    }

    private static <T extends cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity> Map<UUID, T> byId(
            Iterable<T> entities
    ) {
        java.util.HashMap<UUID, T> result = new java.util.HashMap<>();
        entities.forEach(entity -> result.put(entity.getId(), entity));
        return result;
    }

    private static Collection<UUID> nonNullIds(Collection<UUID> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
