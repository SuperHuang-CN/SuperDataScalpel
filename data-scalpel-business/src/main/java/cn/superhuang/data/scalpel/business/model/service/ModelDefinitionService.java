package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelFieldInput;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;


@Service
class ModelDefinitionService {


    private final DataModelRepository repository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final StandardDictionaryValueSupport standardDictionaryValueSupport;
    public ModelDefinitionService(
            DataModelRepository repository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            StandardDictionaryValueSupport standardDictionaryValueSupport
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.standardDictionaryValueSupport = standardDictionaryValueSupport;
    }


    DataModel requireModel(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
    }

    DataSource requireStorageDataSource(UUID id, boolean requireEnabled) {
        return requireModelDataSource(id, requireEnabled, PhysicalTableMode.MANAGED);
    }

    DataSource requireModelDataSource(
            UUID id,
            boolean requireEnabled,
            PhysicalTableMode physicalTableMode
    ) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        physicalTableMode == PhysicalTableMode.MANAGED ? "数据存储不存在" : "JDBC 数据源不存在"
                ));
        if (!dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型只能绑定 JDBC 类型的数据源");
        }
        if (physicalTableMode == PhysicalTableMode.MANAGED
                && !dataSource.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型只能绑定具有数据存储用途的数据源");
        }
        if (physicalTableMode == PhysicalTableMode.MANAGED && dataSource.getType().isTdEngine()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "TDengine 第一版只能绑定已有超级表，不能创建受管模型"
            );
        }
        if (requireEnabled && !dataSource.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    physicalTableMode == PhysicalTableMode.MANAGED ? "关联的数据存储已停用" : "关联的 JDBC 数据源已停用"
            );
        }
        return dataSource;
    }

    List<DataModelField> fieldsFor(UUID modelId) {
        return fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId);
    }

    static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }

    static PlatformTypeDefinition platformType(
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            GeometryTypeDefinition geometry
    ) {
        return switch (type) {
            case STRING -> PlatformTypeDefinition.string(length);
            case DECIMAL -> PlatformTypeDefinition.decimal(precision, scale);
            case GEOMETRY -> PlatformTypeDefinition.geometry(geometry);
            default -> PlatformTypeDefinition.of(type);
        };
    }

    static void validateClickHouseOrderByFields(
            DataSource storage,
            DataModel model,
            List<String> fieldCodes,
            List<PlatformDataType> fieldTypes
    ) {
        if (storage.getType() != DataSourceType.CLICKHOUSE) {
            return;
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            if (!model.getClickHouseOrderByColumns().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定已有表模式不能配置 ClickHouse 排序键");
            }
            return;
        }
        Map<String, PlatformDataType> fieldTypesByCode = new HashMap<>();
        for (int index = 0; index < fieldCodes.size(); index++) {
            fieldTypesByCode.put(fieldCodes.get(index), fieldTypes.get(index));
        }
        for (String orderByColumn : model.getClickHouseOrderByColumns()) {
            PlatformDataType fieldType = fieldTypesByCode.get(orderByColumn);
            if (fieldType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ClickHouse 排序键字段不存在：" + orderByColumn);
            }
            if (fieldType == PlatformDataType.GEOMETRY) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "ClickHouse Geometry 字段不能作为排序键：" + orderByColumn
                );
            }
        }
        if (fieldTypes.contains(PlatformDataType.BINARY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ClickHouse 受控物理表暂不支持二进制字段");
        }
    }

    static ResponseStatusException remoteAccessException(DatabaseAccessException exception) {
        HttpStatus status = switch (exception.code()) {
            case "TABLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_QUERY" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    static List<NormalizedField> normalizeFields(List<DataModelFieldInput> fields) {
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
            GeometryTypeDefinition geometry = null;
            if (input.fieldType() == PlatformDataType.STRING) {
                if (input.geometry() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
                }
                length = input.length();
            } else if (input.fieldType() == PlatformDataType.DECIMAL) {
                if (input.geometry() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
                }
                if (input.precision() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段必须指定精度：" + code);
                }
                precision = input.precision();
                scale = input.scale() == null ? 0 : input.scale();
                if (scale > precision) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段的小数位不能超过精度：" + code);
                }
            } else if (input.fieldType() == PlatformDataType.GEOMETRY) {
                if (input.geometry() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Geometry 字段必须指定几何类型、CRS 和坐标维度：" + code
                    );
                }
                if (input.length() != null || input.precision() != null || input.scale() != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Geometry 字段不能设置长度、精度或小数位：" + code
                    );
                }
                if (!"EPSG".equals(input.geometry().crs().authority())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段第一版只支持 EPSG CRS：" + code);
                }
                if (input.geometry().dimension() != CoordinateDimension.XY) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段第一版只支持 XY 二维坐标：" + code);
                }
                if (input.primaryKey()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段不能作为主键：" + code);
                }
                geometry = input.geometry();
            } else if (input.geometry() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
            }
            normalized.add(new NormalizedField(input, code, length, precision, scale, geometry));
        }
        return normalized;
    }

    void validateStandardDictionaryAssignments(
            List<NormalizedField> fields,
            Map<UUID, DataModelField> currentFields
    ) {
        for (NormalizedField field : fields) {
            DataModelField current = field.input().id() == null ? null : currentFields.get(field.input().id());
            standardDictionaryValueSupport.validateAssignment(
                    field.input().standardDictionaryId(),
                    current == null ? null : current.getStandardDictionaryId(),
                    field.input().fieldType(),
                    field.length(),
                    field.precision(),
                    field.scale()
            );
        }
    }

    static String normalizeCode(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    record NormalizedField(
            DataModelFieldInput input,
            String code,
            Integer length,
            Integer precision,
            Integer scale,
            GeometryTypeDefinition geometry
    ) {
    }

    record ModelOperationPreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            DataSource storage,
            List<DataModelField> fields
    ) {
    }
}
