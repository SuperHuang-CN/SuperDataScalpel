package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
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
    void spatialFileOutputSummaryIncludesSchemaIdentityWithoutRuntimeValues() {
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
            assertTrue(summary.contains("geometryKind=POLYGON"));
            assertTrue(summary.contains("crs=EPSG:4326"));
            assertTrue(summary.contains("dimension=XY"));
            assertFalse(summary.contains("116.397"));
            assertFalse(summary.contains("secret district"));
            assertFalse(summary.contains("s3://"));
        }
    }

    private static String summary(FileOutputFormatOptions formatOptions) {
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                "a62f3130-c220-4ca7-b901-42f0fc80bb15",
                "空间文件输出",
                new CanvasNodeLayout(0D, 0D, 260D, 120D),
                new FileOutputConfiguration(
                        "district_orders",
                        DATA_SOURCE_ID.toString(),
                        "exports/district-orders",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        formatOptions));
        return CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA, List.of(SOURCE_TABLE));
    }
}
