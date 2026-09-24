package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.*;
import cn.superhuang.data.scalpel.business.filedataset.repository.*;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetSchemaValidator;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableSpatialReferenceRequest;
import cn.superhuang.data.scalpel.contract.type.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileDatasetSpatialReferenceTest {
    @ParameterizedTest
    @EnumSource(value = FileDatasetType.class, names = {"GDB", "SHP"})
    void flushesDeletedFieldNamesBeforeRecreatingSpatialSchemaWithoutChangingRowCount(FileDatasetType type)
            throws Exception {
        UUID datasetId = UUID.randomUUID(), tableId = UUID.randomUUID(), fileId = UUID.randomUUID();
        var datasets = mock(FileDatasetRepository.class);
        var files = mock(FileDatasetFileRepository.class);
        var tables = mock(FileDatasetTableRepository.class);
        var sources = mock(FileDatasetTableSourceRepository.class);
        var fields = mock(FileDatasetFieldRepository.class);
        var dataset = mock(FileDataset.class);
        var file = mock(FileDatasetFile.class);
        var table = mock(FileDatasetTable.class);
        var source = mock(FileDatasetTableSource.class);
        when(datasets.findLockedById(datasetId)).thenReturn(Optional.of(dataset));
        when(dataset.getId()).thenReturn(datasetId);
        when(dataset.getType()).thenReturn(type);
        when(dataset.getParsingOptions()).thenReturn("{}");
        when(tables.findLockedByIdAndFileDatasetId(tableId, datasetId)).thenReturn(Optional.of(table));
        when(table.getId()).thenReturn(tableId);
        when(table.hasData()).thenReturn(true);
        when(sources.findByFileDatasetTableIdOrderBySourceOrderAsc(tableId)).thenReturn(List.of(source));
        when(source.getId()).thenReturn(UUID.randomUUID());
        when(source.getSourceFileId()).thenReturn(fileId);
        when(source.getSourceKey()).thenReturn("layer");
        when(source.getRowCount()).thenReturn(100_000L);
        when(files.findAllById(any())).thenReturn(List.of(file));
        when(file.getId()).thenReturn(fileId);
        when(file.getFileDatasetId()).thenReturn(datasetId);
        when(file.getStatus()).thenReturn(FileDatasetFileStatus.READY);
        when(file.getStorageKind()).thenReturn(FileDatasetStorageKind.SINGLE_OBJECT);
        when(file.getObjectKey()).thenReturn("test-only/archive.zip");
        when(file.getFormat()).thenReturn(FileDatasetFormat.valueOf(type.name()));
        when(file.getCompression()).thenReturn(FileDatasetCompression.ZIP);
        var parser = mock(FileDatasetContentParser.class);
        var geometry = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.POLYGON, CrsReference.epsg(4326), CoordinateDimension.XY));
        var parsed = new FileDatasetParser.ParseResult(
                List.of(new FileDatasetParser.Field("geometry", 0, geometry, true)),
                List.of(), true, true, Map.of(), 1_000);
        when(parser.parse(any(), eq(1_000))).thenReturn(parsed);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var service = new FileDatasetService(datasets, files, tables, sources, fields,
                mock(FileDatasetParseJobRepository.class), null, null, null, new ObjectMapper(),
                List.of(), null, parser, null, new FileDatasetSchemaValidator(), null, null, transactions);

        var response = service.updateTableSpatialReference(datasetId, tableId,
                new UpdateFileDatasetTableSpatialReferenceRequest("EPSG", 4326));

        var order = inOrder(fields);
        order.verify(fields).deleteByFileDatasetTableId(tableId);
        order.verify(fields).flush();
        order.verify(fields).saveAll(any());
        assertEquals(100_000L, response.totalRowCount());
        assertEquals(1, response.sourceCount());
        verify(sources, never()).delete(any(FileDatasetTableSource.class));
        verify(transactions, times(2)).commit(any());
    }
}
