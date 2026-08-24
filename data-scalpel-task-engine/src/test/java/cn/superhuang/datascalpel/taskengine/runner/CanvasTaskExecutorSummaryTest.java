package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ColumnDerivation;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputWrite;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.RuntimeValueExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasRuntimeValue;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasTaskExecutorSummaryTest {

    private static final UUID DATA_SOURCE_ID = UUID.fromString("901e8938-bc1d-4bfd-91ec-d26bca38e8f6");
    private static final MetadataIndex EMPTY_METADATA = MetadataIndex.create(
            new MetadataSnapshot(List.of(), List.of()));
    private static final CanvasTableSchema SOURCE_TABLE = new CanvasTableSchema(
            "district_orders",
            CanvasTableOrigin.jdbc(DATA_SOURCE_ID, "district_orders"),
            List.of(new CanvasColumnSchema(
                    "geom",
                    PlatformDataType.GEOMETRY,
                    null,
                    null,
                    null,
                    true,
                    null,
                    false,
                    false,
                    null,
                    new GeometryTypeDefinition(
                            GeometryKind.POLYGON,
                            CrsReference.epsg(4326),
                            CoordinateDimension.XY))));

    @Test
    void spatialFileOutputSummaryIncludesAllWritesWithoutRuntimeValues() {
        String geoParquet = summary(new FileOutputFormatOptions.GeoParquet(
                "geom",
                GeoParquetCompressionCodec.SNAPPY,
                GeoParquetCoveringMode.ROW_BBOX));
        String geoJson = summary(new FileOutputFormatOptions.GeoJson(
                "district_orders",
                "geom",
                null,
                true));

        for (String summary : List.of(geoParquet, geoJson)) {
            assertTrue(summary.contains("writeCount=1"));
            assertTrue(summary.contains("district_orders"));
            assertTrue(summary.contains("exports/district-orders"));
            assertTrue(summary.contains("conflictPolicies=FAIL_IF_EXISTS"));
            assertFalse(summary.contains("116.397"));
            assertFalse(summary.contains("secret district"));
            assertFalse(summary.contains("s3://"));
        }
    }

    @Test
    void multiTableDeriveSummaryUsesGlobalAndOperationRules() {
        ColumnDerivation global = new ColumnDerivation(
                "source_record_hash",
                new LiteralExpression(new CanvasLiteral(PlatformDataType.STRING, "do-not-log")));
        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                "20761935-b98f-4be9-8fd2-24f8ea1d0323",
                "派生字段",
                new CanvasNodeLayout(0D, 0D, 352D, 224D),
                new DeriveColumnsConfiguration(
                        List.of(global),
                        List.of(
                                deriveOperation("orders", "order_hash"),
                                deriveOperation("customers", "customer_hash"),
                                new DeriveColumnsOperation(
                                        UUID.randomUUID().toString(),
                                        "products",
                                        new ProcessorOutput.ReplaceSource("products"),
                                        List.of()))));

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("tableCount=3"));
        assertTrue(summary.contains("globalDerivationCount=1"));
        assertTrue(summary.contains("localDerivationCount=2"));
        assertTrue(summary.contains("effectiveDerivationCount=5"));
        assertTrue(summary.contains("source_record_hash"));
        assertFalse(summary.contains("do-not-log"));
    }

    @Test
    void deriveSummaryRecordsRuntimeValueNamesWithoutResolvingThem() {
        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                "20761935-b98f-4be9-8fd2-24f8ea1d0323",
                "派生字段",
                new CanvasNodeLayout(0D, 0D, 352D, 224D),
                new DeriveColumnsConfiguration(
                        List.of(new ColumnDerivation(
                                "etl_batch_id",
                                new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_ID))),
                        List.of(deriveOperation("orders", "order_hash"))));

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("expressionKinds=COLUMN,RUNTIME_VALUE"));
        assertTrue(summary.contains("runtimeValues=EXECUTION_ID"));
        assertFalse(summary.contains("00000000-0000-0000-0000-000000000000"));
    }

    private static DeriveColumnsOperation deriveOperation(String tableName, String targetColumn) {
        return new DeriveColumnsOperation(
                UUID.randomUUID().toString(),
                tableName,
                new ProcessorOutput.ReplaceSource(tableName),
                List.of(new ColumnDerivation(
                        targetColumn,
                        new ColumnExpression("id"))));
    }

    private static String summary(FileOutputFormatOptions formatOptions) {
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                "a62f3130-c220-4ca7-b901-42f0fc80bb15",
                "空间文件输出",
                new CanvasNodeLayout(0D, 0D, 260D, 120D),
                new FileOutputConfiguration(
                        DATA_SOURCE_ID.toString(),
                        List.of(new FileOutputWrite(
                                UUID.randomUUID().toString(),
                                "district_orders",
                                "exports/district-orders",
                                FileOutputConflictPolicy.FAIL_IF_EXISTS,
                                formatOptions))));
        return CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA, List.of(SOURCE_TABLE));
    }
}
