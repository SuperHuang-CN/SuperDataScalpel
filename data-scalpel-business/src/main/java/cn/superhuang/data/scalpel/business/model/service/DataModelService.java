package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelFieldType;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelFieldInput;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataModelService {

    private final DataModelRepository repository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ModelPhysicalTablePort physicalTablePort;

    public DataModelService(
            DataModelRepository repository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ModelPhysicalTablePort physicalTablePort
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.physicalTablePort = physicalTablePort;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataModelResponse> search(SearchRequest request) {
        Page<DataModel> result = searchEngine.search(request, DataModel.class, repository);
        Map<UUID, String> storageNames = storageNames(result.getContent());
        return new PageResponse<>(
                result.getContent().stream()
                        .map(model -> DataModelResponse.from(model, storageNames.get(model.getStorageDataSourceId())))
                        .toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataModelDetailResponse get(UUID id) {
        return detail(requireModel(id));
    }

    @Transactional
    public DataModelDetailResponse create(CreateDataModelRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        requireStorageDataSource(request.storageDataSourceId(), false);

        String catalogName = normalizeOptional(request.catalogName());
        String schemaName = normalizeOptional(request.schemaName());
        String physicalTableName = normalizeCode(request.physicalTableName());
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(), catalogName, schemaName, physicalTableName, null
        );

        DataModel model = DataModel.create(
                code, request.name(), request.directoryId(), request.storageDataSourceId(),
                catalogName, schemaName, physicalTableName, request.description()
        );
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public DataModelDetailResponse update(UUID id, UpdateDataModelRequest request) {
        DataModel model = requireModel(id);
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());

        String catalogName = normalizeOptional(request.catalogName());
        String schemaName = normalizeOptional(request.schemaName());
        String physicalTableName = normalizeCode(request.physicalTableName());
        boolean physicalDefinitionChanged = !Objects.equals(model.getStorageDataSourceId(), request.storageDataSourceId())
                || !Objects.equals(model.getCatalogName(), catalogName)
                || !Objects.equals(model.getSchemaName(), schemaName)
                || !Objects.equals(model.getPhysicalTableName(), physicalTableName);
        if (model.getStatus() != DataModelStatus.DRAFT && physicalDefinitionChanged) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型不能修改数据存储或物理位置");
        }
        if (model.getStatus() == DataModelStatus.DRAFT) {
            requireStorageDataSource(request.storageDataSourceId(), false);
            validatePhysicalLocationAvailable(
                    request.storageDataSourceId(), catalogName, schemaName, physicalTableName, id
            );
        }

        model.update(
                request.name(), request.directoryId(), request.storageDataSourceId(), catalogName, schemaName,
                physicalTableName, request.description()
        );
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public DataModelDetailResponse updateFields(UUID id, UpdateDataModelFieldsRequest request) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以修改字段结构");
        }

        List<NormalizedField> normalizedFields = normalizeFields(request.fields());
        Map<UUID, DataModelField> existingFields = fieldRepository
                .findAllByModelIdOrderBySortOrderAscCodeAsc(id).stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Set<UUID> retainedIds = new HashSet<>();
        List<DataModelField> fieldsToSave = new ArrayList<>();

        for (NormalizedField normalized : normalizedFields) {
            DataModelFieldInput input = normalized.input();
            DataModelField field;
            if (input.id() == null) {
                field = DataModelField.create(
                        id, normalized.code(), input.name(), input.fieldType(), normalized.length(),
                        normalized.precision(), normalized.scale(), input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
            } else {
                field = existingFields.get(input.id());
                if (field == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段不存在或不属于当前模型");
                }
                if (!retainedIds.add(input.id())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段 ID 重复");
                }
                field.update(
                        normalized.code(), input.name(), input.fieldType(), normalized.length(),
                        normalized.precision(), normalized.scale(), input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
            }
            fieldsToSave.add(field);
        }

        List<DataModelField> removedFields = existingFields.values().stream()
                .filter(field -> !retainedIds.contains(field.getId()))
                .toList();
        if (!removedFields.isEmpty()) {
            fieldRepository.deleteAll(removedFields);
            fieldRepository.flush();
        }
        fieldRepository.saveAllAndFlush(fieldsToSave);
        return detail(model);
    }

    @Transactional
    public DataModelDetailResponse publish(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以发布");
        }
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(id);
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        requireStorageDataSource(model.getStorageDataSourceId(), true);
        physicalTablePort.prepareForPublish(model, List.copyOf(fields));
        model.publish();
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public DataModelDetailResponse disable(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布模型可以停用");
        }
        model.disable();
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public DataModelDetailResponse enable(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已停用模型可以启用");
        }
        requireStorageDataSource(model.getStorageDataSourceId(), true);
        model.publish();
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public void delete(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() == DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型请先停用后再删除");
        }
        fieldRepository.deleteAllByModelId(id);
        repository.delete(model);
    }

    private DataModelDetailResponse detail(DataModel model) {
        String storageName = dataSourceRepository.findById(model.getStorageDataSourceId())
                .map(DataSource::getName)
                .orElse("已删除的数据存储");
        List<DataModelFieldResponse> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId())
                .stream().map(DataModelFieldResponse::from).toList();
        return new DataModelDetailResponse(DataModelResponse.from(model, storageName), fields, false);
    }

    private Map<UUID, String> storageNames(List<DataModel> models) {
        Set<UUID> ids = models.stream().map(DataModel::getStorageDataSourceId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        dataSourceRepository.findAllById(ids).forEach(source -> names.put(source.getId(), source.getName()));
        return names;
    }

    private DataModel requireModel(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
    }

    private DataSource requireStorageDataSource(UUID id, boolean requireEnabled) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据存储不存在"));
        if (!dataSource.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型只能绑定具有数据存储用途的数据源");
        }
        if (requireEnabled && !dataSource.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联的数据存储已停用");
        }
        return dataSource;
    }

    private void validatePhysicalLocationAvailable(
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            UUID currentId
    ) {
        boolean duplicate = currentId == null
                ? repository.existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableName(
                        storageDataSourceId, catalogName, schemaName, physicalTableName)
                : repository.existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableNameAndIdNot(
                        storageDataSourceId, catalogName, schemaName, physicalTableName, currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一数据存储下的物理表位置已被其他模型使用");
        }
    }

    private static List<NormalizedField> normalizeFields(List<DataModelFieldInput> fields) {
        Set<String> codes = new HashSet<>();
        List<NormalizedField> normalized = new ArrayList<>();
        for (DataModelFieldInput input : fields) {
            String code = normalizeCode(input.code());
            if (!codes.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段编码不能重复：" + code);
            }
            if (input.primaryKey() && input.nullable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主键字段不能为空：" + code);
            }

            Integer length = null;
            Integer precision = null;
            Integer scale = null;
            if (input.fieldType() == DataModelFieldType.STRING) {
                if (input.length() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字符串字段必须指定长度：" + code);
                }
                length = input.length();
            } else if (input.fieldType() == DataModelFieldType.DECIMAL) {
                if (input.precision() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段必须指定精度：" + code);
                }
                precision = input.precision();
                scale = input.scale() == null ? 0 : input.scale();
                if (scale > precision) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段的小数位不能超过精度：" + code);
                }
            }
            normalized.add(new NormalizedField(input, code, length, precision, scale));
        }
        return normalized;
    }

    private static String normalizeCode(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record NormalizedField(
            DataModelFieldInput input,
            String code,
            Integer length,
            Integer precision,
            Integer scale
    ) {
    }
}
