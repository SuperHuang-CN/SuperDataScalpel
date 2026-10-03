package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.*;
import cn.superhuang.data.scalpel.business.filedataset.repository.*;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelSpatialPreviewResponse;
import cn.superhuang.data.scalpel.business.spatialpreview.service.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.proj4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class FileDatasetSpatialPreviewService {
    private final FileDatasetRepository datasets;
    private final FileDatasetTableRepository tables;
    private final FileDatasetTableSourceRepository sources;
    private final FileDatasetFileRepository files;
    private final FileDatasetFieldRepository fields;
    private final FileDatasetContentParser parser;
    private final TransactionTemplate tx;

    public FileDatasetSpatialPreviewService(FileDatasetRepository datasets,FileDatasetTableRepository tables,
            FileDatasetTableSourceRepository sources,FileDatasetFileRepository files,FileDatasetFieldRepository fields,
            FileDatasetContentParser parser,PlatformTransactionManager manager) {
        this.datasets=datasets;this.tables=tables;this.sources=sources;this.files=files;this.fields=fields;this.parser=parser;
        tx=new TransactionTemplate(manager);tx.setReadOnly(true);
    }
    public DataModelSpatialPreviewResponse inspect(UUID datasetId,UUID tableId) {
        Snapshot snapshot=snapshot(datasetId,tableId);
        var geometryFields=snapshot.fields().stream().filter(f -> f.getGeometry()!=null).map(f -> {
            var g=f.getGeometry(); boolean allowed=g.dimension()==CoordinateDimension.XY;
            return new DataModelSpatialPreviewResponse.GeometryField(f.getName(),f.getName(),g.kind(),g.crs(),false,snapshot.count(),false,
                    allowed,allowed?null:"目前只支持二维 XY 空间预览");
        }).toList();
        return new DataModelSpatialPreviewResponse(!geometryFields.isEmpty(),geometryFields.isEmpty()?"当前表没有空间字段":null,
                geometryFields,"EPSG:3857",List.of(-180d,-85d,180d,85d),
                new DataModelSpatialPreviewResponse.Limits(256,1600,256,1200,SpatialPreviewBudget.FEATURES,4_000_000,SpatialPreviewBudget.DETAIL_BYTES));
    }
    public SpatialPreviewSource source(UUID datasetId,UUID tableId,String field) {
        Snapshot snapshot=snapshot(datasetId,tableId);
        var geometry=snapshot.fields().stream().filter(f -> f.getName().equals(field)&&f.getGeometry()!=null).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"空间字段不存在")).getGeometry();
        if(geometry.dimension()!=CoordinateDimension.XY) throw new ResponseStatusException(HttpStatus.CONFLICT,"目前只支持二维 XY 空间预览");
        return new SpatialPreviewSource(SpatialPreviewService.fingerprint("file:"+tableId+":"+field),snapshot.revision(),snapshot.count(),snapshot.updating(),sink -> {
            CoordinateTransform transform=null;
            if(geometry.crs().code()!=3857) {
                CRSFactory factory=new CRSFactory();
                transform=new CoordinateTransformFactory().createTransform(factory.createFromName("EPSG:"+geometry.crs().code()),factory.createFromName("EPSG:3857"));
            }
            var projection=transform;
            var from=new ProjCoordinate(); var to=new ProjCoordinate();
            for(var input:snapshot.inputs()) parser.readGeometry(input,field,g -> {
                if(g==null) { sink.accept(null); return; }
                if(g.getNumPoints()>SpatialPreviewBudget.FEATURE_COORDINATES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 100 万坐标");
                if(projection!=null) {
                    g.apply((CoordinateFilter)c -> {
                        from.x=c.x;from.y=c.y;projection.transform(from,to);c.x=to.x;c.y=to.y;
                    });
                    g.geometryChanged();
                }
                sink.acceptGeometry(g);
            });
        },() -> snapshot(datasetId,tableId).revision());
    }
    private Snapshot snapshot(UUID datasetId,UUID tableId) {
        return Objects.requireNonNull(tx.execute(status -> {
            var dataset=datasets.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"文件数据集不存在"));
            var table=tables.findById(tableId).filter(t -> t.getFileDatasetId().equals(datasetId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"文件逻辑表不存在"));
            var current=sources.findByFileDatasetTableIdOrderBySourceOrderAsc(tableId);
            var geometryFields=fields.findByFileDatasetTableIdOrderBySortOrderAsc(tableId);
            StringBuilder version=new StringBuilder(tableId+":"+table.getCurrentLoadJobId()+":"+table.getSpatialReferenceOverride());
            List<FileDatasetContentParser.Input> inputs=new ArrayList<>();long count=0;
            for(var source:current) {
                var file=files.findById(source.getSourceFileId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"来源文件已删除"));
                version.append(':').append(source.getId()).append(':').append(source.getUpdatedAt()).append(':').append(file.getObjectKey()).append(':').append(file.getUpdatedAt());
                count+=source.getRowCount();
                inputs.add(new FileDatasetContentParser.Input(file.getFormat(),file.getCompression(),
                        file.getStorageKind()==FileDatasetStorageKind.SINGLE_OBJECT?file.getObjectKey():file.getMaterializedPrefix(),
                        file.getSizeBytes(),dataset.getParsingOptions(),source.getSourceKey(),
                        table.getSpatialReferenceOverride()==null?null:table.getSpatialReferenceOverride().code()));
            }
            for(var field:geometryFields) version.append(':').append(field.getName()).append(':').append(field.getGeometry());
            if(!table.hasData() && table.getCurrentLoadJobId()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"文件表尚未就绪，请先完成解析或确认空间参考");
            return new Snapshot(SpatialPreviewService.fingerprint(version.toString()),count,table.getCurrentLoadJobId()!=null,geometryFields,inputs);
        }));
    }
    private record Snapshot(String revision,long count,boolean updating,List<FileDatasetField> fields,List<FileDatasetContentParser.Input> inputs) { }
}
