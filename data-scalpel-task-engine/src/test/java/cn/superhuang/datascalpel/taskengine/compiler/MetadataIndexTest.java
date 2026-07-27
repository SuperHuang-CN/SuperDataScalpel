package cn.superhuang.datascalpel.taskengine.compiler;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.http.TaskEngineException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetadataIndexTest {

    @Test
    void indexesModelsByIdAndCodeAndBuildsAModelOriginTable() {
        UUID dataSourceId = UUID.randomUUID();
        MetadataModel model = model(UUID.randomUUID(), dataSourceId, "order_detail");

        MetadataIndex index = MetadataIndex.create(snapshot(dataSourceId, List.of(model)));

        assertSame(model, index.model(model.id()).metadata());
        assertSame(model, index.modelByCode(model.code()).metadata());
        assertEquals("order_detail", index.model(model.id()).tableSchema().name());
        assertEquals("MODEL", index.model(model.id()).tableSchema().origin().kind());
        assertEquals(model.id(), index.model(model.id()).tableSchema().origin().modelId());
        assertEquals(3, index.model(model.id()).tableSchema().origin().modelSchemaVersion());
    }

    @Test
    void rejectsDuplicateCodesDuplicateFieldsAndMissingDataSources() {
        UUID dataSourceId = UUID.randomUUID();
        MetadataModel first = model(UUID.randomUUID(), dataSourceId, "orders");
        MetadataModel duplicateCode = model(UUID.randomUUID(), dataSourceId, "orders");

        assertInvalid(snapshot(dataSourceId, List.of(first, duplicateCode)), ".code duplicates model code");

        MetadataModel duplicateFields = new MetadataModel(
                first.id(), first.code(), first.name(), first.schemaVersion(), first.status(),
                first.physicalTableMode(), first.dataSourceId(), first.catalogName(), first.schemaName(),
                first.physicalTableName(), List.of(column("id"), column("id"))
        );
        assertInvalid(snapshot(dataSourceId, List.of(duplicateFields)), ".name duplicates column id");

        MetadataModel missingDataSource = model(UUID.randomUUID(), UUID.randomUUID(), "customers");
        assertInvalid(snapshot(dataSourceId, List.of(missingDataSource)), ".dataSourceId references missing data source");
    }

    private static void assertInvalid(MetadataSnapshot snapshot, String messagePart) {
        TaskEngineException exception = assertThrows(TaskEngineException.class, () -> MetadataIndex.create(snapshot));
        assertEquals("INVALID_METADATA_SNAPSHOT", exception.code());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains(messagePart), exception.getMessage());
    }

    private static MetadataSnapshot snapshot(UUID dataSourceId, List<MetadataModel> models) {
        return new MetadataSnapshot(
                List.of(new MetadataDataSource(
                        dataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.STORAGE),
                        List.of()
                )),
                models
        );
    }

    private static MetadataModel model(UUID id, UUID dataSourceId, String code) {
        return new MetadataModel(
                id,
                code,
                "订单明细",
                3,
                MetadataModelStatus.PUBLISHED,
                MetadataModelPhysicalTableMode.MANAGED,
                dataSourceId,
                "warehouse",
                "public",
                "dwd_" + code,
                List.of(column("id"))
        );
    }

    private static CanvasColumnSchema column(String name) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.LONG,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                "主键"
        );
    }
}
