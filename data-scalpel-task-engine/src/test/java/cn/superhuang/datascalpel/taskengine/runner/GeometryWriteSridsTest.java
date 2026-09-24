package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryWriteSridsTest {
    @Test
    void resolvesEachTargetFieldFromSnapshotWithoutADatabaseConnection() {
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(
                geometry("geom", CrsReference.epsg(4326)),
                geometry("projected_geom", CrsReference.epsg(3857))
        ));

        assertEquals(Map.of("geom", 4326, "projected_geom", 3857),
                SpatialJdbcRuntimeSupport.resolveGeometryWriteSrids(
                        target, new String[]{"geom", "projected_geom"}, "output"));
    }

    @Test
    void onlyResolvesGeometryFieldsParticipatingInTheWrite() {
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(
                new CanvasColumnSchema("id", PlatformDataType.LONG, null, null, null,
                        false, null, false, false, null),
                geometry("geom", CrsReference.epsg(4490)),
                geometry("unused_geom", new CrsReference("CUSTOM", 1234))
        ));

        assertEquals(Map.of("geom", 4490), SpatialJdbcRuntimeSupport.resolveGeometryWriteSrids(
                target, new String[]{"id", "geom"}, "output"));
        assertTrue(SpatialJdbcRuntimeSupport.resolveGeometryWriteSrids(
                target, new String[]{"id"}, "output").isEmpty());
    }

    @Test
    void rejectsNonEpsgCrsWithFieldAndNodeContext() {
        CanvasTableSchema target = new CanvasTableSchema("target", null,
                List.of(geometry("geom", new CrsReference("CUSTOM", 4326))));

        RunnerExecutionException failure = assertThrows(RunnerExecutionException.class,
                () -> SpatialJdbcRuntimeSupport.resolveGeometryWriteSrids(
                        target, new String[]{"geom"}, "output"));

        assertEquals("SPATIAL_TARGET_METADATA_UNAVAILABLE", failure.code());
        assertEquals("output", failure.nodeId());
        assertTrue(failure.getMessage().contains("geom"));
        assertTrue(failure.getMessage().contains("EPSG CRS"));
    }

    @Test
    void snapshotContractsRejectMissingGeometryOrCrsAndInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> new CanvasColumnSchema(
                "geom", PlatformDataType.GEOMETRY, null, null, null,
                true, null, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> geometry("geom", null));
        assertThrows(IllegalArgumentException.class, () -> CrsReference.epsg(0));
    }

    private static CanvasColumnSchema geometry(String name, CrsReference crs) {
        return new CanvasColumnSchema(name, PlatformDataType.GEOMETRY, null, null, null,
                true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POLYGON, crs, CoordinateDimension.XY));
    }
}
