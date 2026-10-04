package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalStatistics;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalStatisticsRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelSpatialPreviewResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class DataModelSpatialPreviewService {

    private static final int MINIMUM_WIDTH = 256;
    private static final int MAXIMUM_WIDTH = 1_600;
    private static final int MINIMUM_HEIGHT = 256;
    private static final int MAXIMUM_HEIGHT = 1_200;
    private static final int MAXIMUM_FEATURES = 1_000_000;
    private static final int MAXIMUM_COORDINATES = 4_000_000;
    private static final long MAXIMUM_WKB_BYTES = 64L * 1024L * 1024L;
    private static final List<Double> INITIAL_BOUNDS = List.of(-180d, -85d, 180d, 85d);
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataModelPhysicalStatisticsRepository statisticsRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ModelPhysicalTablePort physicalTablePort;
    private final cn.superhuang.data.scalpel.business.model.repository.ModelPreviewRevisionRepository revisions;
    private final TransactionTemplate transactionTemplate;
    private final cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService previews;

    public DataModelSpatialPreviewService(
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataModelPhysicalStatisticsRepository statisticsRepository,
            DataSourceRepository dataSourceRepository,
            ModelPhysicalTablePort physicalTablePort,
            PlatformTransactionManager transactionManager,
            cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService previews,
            cn.superhuang.data.scalpel.business.model.repository.ModelPreviewRevisionRepository revisions
    ) {
        this.previews=previews; this.revisions=revisions;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.statisticsRepository = statisticsRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.physicalTablePort = physicalTablePort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public DataModelSpatialPreviewResponse inspect(UUID modelId) {
        Preparation p=snapshot(modelId);
        boolean supported="POSTGRESQL".equals(p.storage().getType().name());
        var fields=p.fields().stream().map(f -> new DataModelSpatialPreviewResponse.GeometryField(
                f.getCode(),f.getName(),f.getGeometry().kind(),f.getGeometry().crs(),false,
                p.statistics()==null?null:p.statistics().rowCount(),false,
                supported && f.getGeometry().dimension()==cn.superhuang.data.scalpel.contract.type.CoordinateDimension.XY,
                !supported?"当前数据库暂不支持空间预览":f.getGeometry().dimension()!=cn.superhuang.data.scalpel.contract.type.CoordinateDimension.XY?"目前只支持二维 XY 空间预览":null )).toList();
        return response(supported&&!fields.isEmpty(),fields.isEmpty()?"模型不包含 Geometry 字段":null,fields);
    }

    public cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewSource source(UUID modelId,String geometryField) {
        Preparation p=snapshot(modelId);
        DataModelField field=p.fields().stream().filter(f -> f.getCode().equals(geometryField)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"空间字段不存在"));
        if(!"POSTGRESQL".equals(p.storage().getType().name())) throw new ResponseStatusException(HttpStatus.CONFLICT,"当前数据库暂不支持空间预览");
        if(field.getGeometry().dimension()!=cn.superhuang.data.scalpel.contract.type.CoordinateDimension.XY)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"目前只支持二维 XY 空间预览");
        var config=p.storage().getConnection().toJdbcConnectionConfig();
        String key=cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService.fingerprint(
                p.storage().getId()+":"+config.host()+":"+config.port()+":"+config.databaseName()+":"+
                (p.model().getSchemaName()==null?config.schemaName():p.model().getSchemaName())+":"+p.model().getPhysicalTableName()+":"+geometryField);
        var writes=revisions.read(modelId);
        String revision=cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService.fingerprint(revision(p,field)+writes.value());
        return new cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewSource(key,revision,
                p.statistics()==null?null:p.statistics().rowCount(),writes.updating(),sink -> {
            var inspection=physicalTablePort.inspect(p.storage(),p.model(),p.allFields());
            if(inspection.state()!=PhysicalTableState.MATCHED) throw new IllegalArgumentException("物理表未就绪："+inspection.message());
            physicalTablePort.streamSpatialPreview(p.storage(),p.model(),previewColumn(field),
                    cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewBudget.FEATURES,Duration.ofSeconds(120),sink);
        },() -> {
            Preparation latest=snapshot(modelId);
            DataModelField current=latest.fields().stream().filter(f -> f.getCode().equals(geometryField)).findFirst().orElseThrow();
            return cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService.fingerprint(revision(latest,current)+revisions.read(modelId).value());
        });
    }
    private static String revision(Preparation p,DataModelField field) {
        return cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService.fingerprint(
                p.storage().getUpdatedAt()+":"+field.getGeometry());
    }

    public SpatialPreviewImage render(UUID id,String field,String bbox,int width,int height) {
        var result=previews.image(source(id,field),null,bbox,width,height,false);
        return new SpatialPreviewImage(result.png(),(int)result.featureCount(),0,false);
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
            PhysicalStatisticsSnapshot statistics = statisticsRepository.findByModelId(modelId)
                    .map(PhysicalStatisticsSnapshot::from)
                    .orElse(null);
            return new Preparation(model, storage, allFields, geometryFields, statistics);
        });
        if (result == null) {
            throw new IllegalStateException("读取空间预览模型快照失败");
        }
        return result;
    }

    private static SpatialPreviewColumn previewColumn(DataModelField field) {
        return new SpatialPreviewColumn(field.getCode(), field.getGeometry());
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
            List<DataModelField> fields,
            PhysicalStatisticsSnapshot statistics
    ) {
    }

    private record PhysicalStatisticsSnapshot(Long rowCount) {
        private static PhysicalStatisticsSnapshot from(DataModelPhysicalStatistics statistics) {
            return new PhysicalStatisticsSnapshot(statistics.getRowCount());
        }
    }
}
