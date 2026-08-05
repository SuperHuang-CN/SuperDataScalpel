package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestVersionSupportTest {

    @Test
    void acceptsVersionEightForTasksWithoutVersionNineCapabilities() {
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                null,
                new MetadataSnapshot(List.of(), List.of())
        );

        assertDoesNotThrow(() -> ManifestVersionSupport.requireSupported(manifest));
    }

    @Test
    void acceptsVersionEightForSpatialNodes() {
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间转换",
                        null,
                        null
                )),
                List.of()
        );
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                new TaskDefinition(TaskType.CANVAS, definition),
                new MetadataSnapshot(List.of(), List.of())
        );

        assertDoesNotThrow(() -> ManifestVersionSupport.requireSupported(manifest));
    }

    @Test
    void acceptsVersionEightForGeometryMetadata() {
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                GeometryKind.POINT,
                CrsReference.epsg(4326),
                CoordinateDimension.XY
        );
        CanvasColumnSchema column = new CanvasColumnSchema(
                "location",
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                true,
                null,
                false,
                false,
                null,
                geometry
        );
        MetadataDataSource dataSource = new MetadataDataSource(
                UUID.randomUUID(),
                true,
                ConnectionKind.JDBC,
                Set.of(DataSourcePurpose.SOURCE),
                List.of(new MetadataTable("orders", DatabaseObjectType.TABLE, List.of(column)))
        );
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                null,
                new MetadataSnapshot(List.of(dataSource), List.of())
        );

        assertDoesNotThrow(() -> ManifestVersionSupport.requireSupported(manifest));
    }

    @Test
    void rejectsVersionEightForJdbcQueryInput() {
        JdbcQueryInputNodeDefinition queryInput = new JdbcQueryInputNodeDefinition(
                UUID.randomUUID().toString(),
                "查询输入",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new JdbcQueryInputConfiguration("", "SELECT 1", "query_result", "", List.of())
        );
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                new TaskDefinition(TaskType.CANVAS, new CanvasDefinition(
                        1, 24, List.of(queryInput), List.of())),
                new MetadataSnapshot(List.of(), List.of())
        );

        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> ManifestVersionSupport.requireSupported(manifest)
        );

        assertEquals("INVALID_MANIFEST", exception.code());
    }

    @Test
    void rejectsVersionEightForJdbcOutputUpsert() {
        JdbcOutputNodeDefinition output = new JdbcOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "UPSERT 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new JdbcOutputConfiguration(
                        "orders", "", "orders", JdbcWriteMode.UPSERT,
                        ColumnMappingMode.BY_NAME, List.of(), List.of("order_id"))
        );
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.PREVIOUS_MANIFEST_VERSION,
                new TaskDefinition(TaskType.CANVAS, new CanvasDefinition(
                        1, 24, List.of(output), List.of())),
                new MetadataSnapshot(List.of(), List.of())
        );

        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> ManifestVersionSupport.requireSupported(manifest)
        );

        assertEquals("INVALID_MANIFEST", exception.code());
    }

    @Test
    void acceptsVersionNineForJdbcQueryInput() {
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new JdbcQueryInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "查询输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new JdbcQueryInputConfiguration("", "SELECT 1", "query_result", "", List.of())
                )),
                List.of()
        );
        TaskExecutionManifest manifest = manifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskDefinition(TaskType.CANVAS, definition),
                new MetadataSnapshot(List.of(), List.of())
        );

        assertDoesNotThrow(() -> ManifestVersionSupport.requireSupported(manifest));
    }

    private static TaskExecutionManifest manifest(
            int version,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot
    ) {
        return new TaskExecutionManifest(version, null, task, metadataSnapshot, List.of());
    }
}
