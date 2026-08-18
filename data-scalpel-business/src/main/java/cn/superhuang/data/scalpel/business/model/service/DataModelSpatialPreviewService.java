package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelSpatialPreviewResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataModelSpatialPreviewService {

    private static final int MINIMUM_WIDTH = 256;
    private static final int MAXIMUM_WIDTH = 1_600;
    private static final int MINIMUM_HEIGHT = 256;
    private static final int MAXIMUM_HEIGHT = 1_200;
    private static final int MAXIMUM_FEATURES = 5_000;
    private static final int MAXIMUM_COORDINATES = 500_000;
    private static final long MAXIMUM_WKB_BYTES = 16L * 1024L * 1024L;
    private static final long UNINDEXED_MAXIMUM_ROWS = 50_000L;
    private static final double WEB_MERCATOR_LIMIT = 20_037_508.342789244d;
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(5);
    private static final List<Double> INITIAL_BOUNDS = List.of(113.5d, 24.4d, 118.6d, 30.2d);
    private static final SpatialPreviewLimits DIALECT_LIMITS = new SpatialPreviewLimits(
            MAXIMUM_FEATURES, MAXIMUM_WKB_BYTES, UNINDEXED_MAXIMUM_ROWS
    );

    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ModelPhysicalTablePort physicalTablePort;
    private final TransactionTemplate transactionTemplate;
    private final SpatialPreviewPngRenderer renderer = new SpatialPreviewPngRenderer();

    public DataModelSpatialPreviewService(
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            ModelPhysicalTablePort physicalTablePort,
            PlatformTransactionManager transactionManager
    ) {
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.physicalTablePort = physicalTablePort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public DataModelSpatialPreviewResponse inspect(UUID modelId) {
        Preparation preparation = snapshot(modelId);
        if (preparation.fields().isEmpty()) {
            return response(false, "模型不包含 Geometry 字段", List.of());
        }
        if (preparation.storage().getType() != DataSourceType.POSTGRESQL) {
            return response(false, "当前仅支持 PostgreSQL/PostGIS 动态空间预览", fieldsWithoutRuntime(preparation.fields()));
        }
        ModelPhysicalTableInspection tableInspection = physicalTablePort.inspect(
                preparation.storage(), preparation.model(), preparation.allFields()
        );
        if (tableInspection.state() != PhysicalTableState.MATCHED) {
            return response(false, "物理表未就绪：" + tableInspection.message(), fieldsWithoutRuntime(preparation.fields()));
        }
        try {
            SpatialPreviewMetadata metadata = physicalTablePort.inspectSpatialPreview(
                    preparation.storage(), preparation.model(), previewColumns(preparation.fields()), QUERY_TIMEOUT
            );
            Map<String, SpatialPreviewColumnMetadata> runtimeByName = metadata.columns().stream()
                    .collect(Collectors.toMap(SpatialPreviewColumnMetadata::name, Function.identity()));
            List<DataModelSpatialPreviewResponse.GeometryField> fields = preparation.fields().stream()
                    .map(field -> responseField(field, runtimeByName.get(field.getCode())))
                    .toList();
            return response(metadata.supported(), metadata.message(), fields);
        } catch (DatabaseAccessException exception) {
            return response(false, exception.getMessage(), fieldsWithoutRuntime(preparation.fields()));
        }
    }

    public SpatialPreviewImage render(
            UUID modelId,
            String geometryField,
            String bbox,
            int width,
            int height
    ) {
        SpatialPreviewViewport viewport = viewport(bbox, width, height);
        Preparation preparation = snapshot(modelId);
        if (preparation.storage().getType() != DataSourceType.POSTGRESQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前仅支持 PostgreSQL/PostGIS 动态空间预览");
        }
        DataModelField field = preparation.fields().stream()
                .filter(candidate -> candidate.getCode().equals(geometryField))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "空间预览字段不存在或不是 Geometry 字段"));
        ModelPhysicalTableInspection tableInspection = physicalTablePort.inspect(
                preparation.storage(), preparation.model(), preparation.allFields()
        );
        if (tableInspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + tableInspection.message());
        }
        try {
            SpatialPreviewData data = physicalTablePort.readSpatialPreview(
                    preparation.storage(), preparation.model(), previewColumn(field),
                    viewport, DIALECT_LIMITS, QUERY_TIMEOUT
            );
            SpatialPreviewPngRenderer.RenderedSpatialPreview rendered = renderer.render(
                    data.geometries(), viewport, MAXIMUM_COORDINATES, data.skippedCount(), data.truncated()
            );
            return new SpatialPreviewImage(
                    rendered.png(), rendered.featureCount(), rendered.skippedCount(), rendered.truncated()
            );
        } catch (DatabaseAccessException exception) {
            HttpStatus status = switch (exception.code()) {
                case "INVALID_SPATIAL_PREVIEW" -> HttpStatus.CONFLICT;
                case "QUERY_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status, exception.getMessage(), exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private Preparation snapshot(UUID modelId) {
        Preparation result = transactionTemplate.execute(status -> {
            DataModel model = modelRepository.findById(modelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
            DataSource storage = dataSourceRepository.findById(model.getStorageDataSourceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型关联的数据源不存在"));
            if (!storage.isEnabled()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型关联的数据源已停用");
            }
            List<DataModelField> allFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId);
            List<DataModelField> geometryFields = allFields.stream()
                    .filter(field -> field.getFieldType() == PlatformDataType.GEOMETRY && field.getGeometry() != null)
                    .toList();
            return new Preparation(model, storage, allFields, geometryFields);
        });
        if (result == null) {
            throw new IllegalStateException("读取空间预览模型快照失败");
        }
        return result;
    }

    private static SpatialPreviewViewport viewport(String bbox, int width, int height) {
        if (width < MINIMUM_WIDTH || width > MAXIMUM_WIDTH
                || height < MINIMUM_HEIGHT || height > MAXIMUM_HEIGHT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "图片宽度必须为 256–1600，高度必须为 256–1200"
            );
        }
        if (bbox == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox 不能为空");
        }
        String[] values = bbox.split(",", -1);
        if (values.length != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox 必须为 minX,minY,maxX,maxY");
        }
        try {
            double minX = Double.parseDouble(values[0].trim());
            double minY = Double.parseDouble(values[1].trim());
            double maxX = Double.parseDouble(values[2].trim());
            double maxY = Double.parseDouble(values[3].trim());
            if (Math.abs(minX) > WEB_MERCATOR_LIMIT || Math.abs(maxX) > WEB_MERCATOR_LIMIT
                    || Math.abs(minY) > WEB_MERCATOR_LIMIT || Math.abs(maxY) > WEB_MERCATOR_LIMIT) {
                throw new IllegalArgumentException("bbox 超出 Web Mercator 有效范围");
            }
            return new SpatialPreviewViewport(minX, minY, maxX, maxY, width, height);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private static List<SpatialPreviewColumn> previewColumns(List<DataModelField> fields) {
        return fields.stream().map(DataModelSpatialPreviewService::previewColumn).toList();
    }

    private static SpatialPreviewColumn previewColumn(DataModelField field) {
        return new SpatialPreviewColumn(field.getCode(), field.getGeometry());
    }

    private static List<DataModelSpatialPreviewResponse.GeometryField> fieldsWithoutRuntime(List<DataModelField> fields) {
        return fields.stream().map(field -> responseField(field, null)).toList();
    }

    private static DataModelSpatialPreviewResponse.GeometryField responseField(
            DataModelField field,
            SpatialPreviewColumnMetadata runtime
    ) {
        return new DataModelSpatialPreviewResponse.GeometryField(
                field.getCode(), field.getName(), field.getGeometry().kind(), field.getGeometry().crs(),
                runtime != null && runtime.spatialIndexAvailable(),
                runtime == null ? null : runtime.estimatedRowCount(),
                runtime != null && runtime.previewAllowed(),
                runtime == null ? null : runtime.message()
        );
    }

    private static DataModelSpatialPreviewResponse response(
            boolean supported,
            String message,
            List<DataModelSpatialPreviewResponse.GeometryField> fields
    ) {
        return new DataModelSpatialPreviewResponse(
                supported, message, fields, "EPSG:3857", INITIAL_BOUNDS,
                new DataModelSpatialPreviewResponse.Limits(
                        MINIMUM_WIDTH, MAXIMUM_WIDTH, MINIMUM_HEIGHT, MAXIMUM_HEIGHT,
                        MAXIMUM_FEATURES, MAXIMUM_COORDINATES, MAXIMUM_WKB_BYTES
                )
        );
    }

    private record Preparation(
            DataModel model,
            DataSource storage,
            List<DataModelField> allFields,
            List<DataModelField> fields
    ) {
    }
}
