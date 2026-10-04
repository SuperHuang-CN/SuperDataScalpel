package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.*;
import cn.superhuang.data.scalpel.business.filedataset.repository.*;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewSource;
import cn.superhuang.data.scalpel.contract.type.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileDatasetSpatialPreviewServiceTest {
    @ParameterizedTest
    @ValueSource(ints={3857,4326})
    void passesGeometryDirectlyToSinkAndOnlyProjectsWhenRequired(int epsg) throws Exception {
        UUID datasetId=UUID.randomUUID(),tableId=UUID.randomUUID(),fileId=UUID.randomUUID();
        var datasets=mock(FileDatasetRepository.class);
        var tables=mock(FileDatasetTableRepository.class);
        var sources=mock(FileDatasetTableSourceRepository.class);
        var files=mock(FileDatasetFileRepository.class);
        var fields=mock(FileDatasetFieldRepository.class);
        var parser=mock(FileDatasetContentParser.class);
        var dataset=mock(FileDataset.class);
        var table=mock(FileDatasetTable.class);
        var file=mock(FileDatasetFile.class);
        var source=mock(FileDatasetTableSource.class);
        var field=mock(FileDatasetField.class);
        when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(tables.findById(tableId)).thenReturn(Optional.of(table));
        when(table.getFileDatasetId()).thenReturn(datasetId);
        when(table.hasData()).thenReturn(true);
        when(files.findById(fileId)).thenReturn(Optional.of(file));
        when(file.getStorageKind()).thenReturn(FileDatasetStorageKind.SINGLE_OBJECT);
        when(file.getObjectKey()).thenReturn("spatial-preview-test.parquet");
        when(file.getFormat()).thenReturn(FileDatasetFormat.GEOPARQUET);
        when(file.getCompression()).thenReturn(FileDatasetCompression.NONE);
        when(dataset.getParsingOptions()).thenReturn("{}");
        when(source.getSourceKey()).thenReturn("table");
        when(source.getSourceFileId()).thenReturn(fileId);
        when(source.getRowCount()).thenReturn(3L);
        when(sources.findByFileDatasetTableIdOrderBySourceOrderAsc(tableId)).thenReturn(List.of(source));
        when(fields.findByFileDatasetTableIdOrderBySortOrderAsc(tableId)).thenReturn(List.of(field));
        when(field.getName()).thenReturn("geometry");
        when(field.getGeometry()).thenReturn(new GeometryTypeDefinition(GeometryKind.POINT,CrsReference.epsg(epsg),CoordinateDimension.XY));
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        Geometry point=new GeometryFactory().createPoint(new Coordinate(116,30));
        Geometry empty=new GeometryFactory().createPoint();
        doAnswer(invocation -> {
            Consumer<Geometry> consumer=invocation.getArgument(2);
            consumer.accept(point);consumer.accept(null);consumer.accept(empty);return null;
        }).when(parser).readGeometry(any(),eq("geometry"),any());
        var service=new FileDatasetSpatialPreviewService(datasets,tables,sources,files,fields,parser,manager);
        var received=new ArrayList<Geometry>();
        service.source(datasetId,tableId,"geometry").reader().read(new SpatialPreviewSource.Sink() {
            @Override public void accept(byte[] value) { assertNull(value);received.add(null); }
            @Override public void acceptGeometry(Geometry value) { received.add(value); }
        });
        assertEquals(3,received.size());assertSame(point,received.getFirst());
        assertNull(received.get(1));assertSame(empty,received.getLast());
        if(epsg==3857) {
            assertEquals(116,point.getCoordinate().x);assertEquals(30,point.getCoordinate().y);
        } else {
            assertEquals(12913060.932,point.getCoordinate().x,0.01);
            assertEquals(3503549.844,point.getCoordinate().y,0.01);
        }
    }
}
