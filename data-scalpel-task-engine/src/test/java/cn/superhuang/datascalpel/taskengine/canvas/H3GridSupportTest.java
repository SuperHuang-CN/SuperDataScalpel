package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class H3GridSupportTest {
    @Test void invalidBoundaryDoesNotLeakNativeInput() {
        var error = assertThrows(IllegalArgumentException.class, () -> H3GridSupport.boundary("private-cell-data"));
        assertEquals("SPATIAL_H3_BOUNDARY_INVALID", error.getMessage()); assertNull(error.getCause());
    }

    @Test void validatesActiveModeOnlyAndReportsPreciseConfigurationProblems() {
        record Case(cn.superhuang.data.scalpel.contract.task.SpatialH3Options options, double size,
                    cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit unit, String error) { }
        var mode = cn.superhuang.data.scalpel.contract.task.SpatialH3Options.Mode.RESOLUTION;
        var approx = cn.superhuang.data.scalpel.contract.task.SpatialH3Options.Mode.APPROXIMATE_SIZE;
        var metres = cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.METERS;
        for (var test : List.of(new Case(null, 1, metres, "SPATIAL_H3_MODE_REQUIRED"),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(mode, null), 1, metres, "INVALID_SPATIAL_H3_RESOLUTION"),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(mode, -1), 1, metres, "INVALID_SPATIAL_H3_RESOLUTION"),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(mode, 16), 1, metres, "INVALID_SPATIAL_H3_RESOLUTION"),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(mode, 0), -1, null, ""),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(approx, 99), 1000, metres, ""),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(approx, 0), 0, metres, "INVALID_SPATIAL_H3_SIZE"),
                new Case(new cn.superhuang.data.scalpel.contract.task.SpatialH3Options(approx, 0), 10,
                        cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.SOURCE_CRS_UNIT, "INVALID_SPATIAL_H3_SIZE"))) {
            var errors = new java.util.ArrayList<String>();
            var sink = new CanvasNodeIssueSink() {
                public void error(String code, String message, String path) { errors.add(code); }
                public void warning(String code, String message, String path) { }
                public boolean hasErrors() { return !errors.isEmpty(); }
            };
            var c = new cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration("", "",
                    cn.superhuang.data.scalpel.contract.task.SpatialBinShape.H3, test.size(), test.unit(), false, List.of(), null, null, "", "", "", null, test.options());
            var r = H3GridSupport.resolve(c, sink);
            if (test.error().isEmpty()) { assertNotNull(r); assertTrue(errors.isEmpty()); }
            else { assertNull(r); assertEquals(List.of(test.error()), errors); }
        }
    }
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
    @Test void supportsEveryBaseCellIncludingPentagonsAndPolarCaps() {
        var core = H3GridSupport.core();
        assertEquals(122, core.getRes0CellAddresses().size());
        for (String cell : core.getRes0CellAddresses()) {
            var geometry = H3GridSupport.boundary(cell);
            assertFalse(geometry.isEmpty(), cell); assertTrue(geometry.isValid(), cell);
            assertEquals(4326, geometry.getSRID());
            var center = core.cellToLatLng(cell);
            assertTrue(geometry.covers(factory.createPoint(new Coordinate(center.lng, center.lat))), cell);
            for (var coordinate : geometry.getCoordinates()) {
                assertTrue(coordinate.x >= -180 && coordinate.x <= 180, cell);
                assertTrue(coordinate.y >= -90 && coordinate.y <= 90, cell);
            }
        }
    }

    @Test void supportsHighResolutionDatelinePolesAndCanonicalMembership() {
        for (int resolution : List.of(0, 6, 15)) {
            for (var coordinate : List.of(new Coordinate(0, 0), new Coordinate(179.9999, 40), new Coordinate(-179.9999, -40), new Coordinate(0, 90), new Coordinate(0, -90))) {
                var point = factory.createPoint(coordinate);
                String cell = H3GridSupport.cell(point, resolution);
                assertEquals(resolution, H3GridSupport.core().getResolution(cell));
                var geometry = H3GridSupport.boundary(cell);
                assertTrue(geometry.isValid(), cell);
                assertTrue(geometry.covers(point), cell + " does not cover " + point);
            }
            assertEquals(H3GridSupport.cell(factory.createPoint(new Coordinate(-180, 20)), resolution), H3GridSupport.cell(factory.createPoint(new Coordinate(180, 20)), resolution));
            assertEquals(H3GridSupport.cell(factory.createPoint(new Coordinate(0, 90)), resolution), H3GridSupport.cell(factory.createPoint(new Coordinate(170, 90)), resolution));
        }
    }

    @Test void resolutionSelectionUsesNearestAverageFlatDiameterAndRejectsInvalidPoints() {
        for (int r = 0; r <= 15; r++) assertEquals(r, H3GridSupport.nearestResolution(H3GridSupport.averageDiameter(r)));
        assertEquals(0, H3GridSupport.nearestResolution(1e20)); assertEquals(15, H3GridSupport.nearestResolution(1e-9));
        assertEquals(8, H3GridSupport.nearestResolution(1000));
        assertNull(H3GridSupport.cell(null, 0)); assertNull(H3GridSupport.cell(factory.createPoint(), 0));
        for (var point : List.of(factory.createPoint(new Coordinate(181, 0)), factory.createPoint(new Coordinate(0, 91)), factory.createPoint(new Coordinate(Double.NaN, 1))))
            assertEquals("SPATIAL_H3_POINT_INVALID", assertThrows(IllegalArgumentException.class, () -> H3GridSupport.cell(point, 2)).getMessage());
    }
}
