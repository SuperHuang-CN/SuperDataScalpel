package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialAreaUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SpatialUnitConversionTest {
    @ParameterizedTest @CsvSource({
            "METERS,1", "KILOMETERS,1000", "FEET,0.3048", "MILES,1609.344", "NAUTICAL_MILES,1852",
            "YARDS,0.9144", "FEET_US,0.3048006096012192", "YARDS_US,0.9144018288036576",
            "MILES_US,1609.347218694438", "NAUTICAL_MILES_US,1853.248"
    })
    void distanceFactorsMatchEsriProjectionDatabase(SpatialDistanceUnit unit, double metres) {
        assertEquals(metres, SpatialDistanceSupport.metresPerConfiguredUnit(unit), metres * 1e-14);
        var resolved = SpatialDistanceSupport.resolve(2d, unit, new CrsReference("EPSG", 3857));
        assertTrue(resolved.valid()); assertEquals(2 * metres, resolved.sourceCrsValue(), metres * 1e-12);
    }

    @ParameterizedTest @CsvSource({
            "SQUARE_METERS,1", "SQUARE_KILOMETERS,1000000", "HECTARES,10000", "ACRES,4046.8564224",
            "SQUARE_FEET,0.09290304", "SQUARE_MILES,2589988.110336", "SQUARE_YARDS,0.83612736",
            "SQUARE_FEET_US,0.09290341161327487", "SQUARE_YARDS_US,0.8361307045194736",
            "SQUARE_MILES_US,2589998.470319522", "ACRES_US,4046.872609874252"
    })
    void areaFactorsMatchEsriProjectionDatabase(SpatialAreaUnit unit, double metresSquared) {
        assertEquals(metresSquared, SpatialDistanceSupport.squareMetresPerConfiguredUnit(unit), metresSquared * 1e-14);
    }

    @Test void sourceAxisIsNotAssumedToBeMetresAndAngularRestrictionsRemain() {
        var feet = new CrsReference("EPSG", 2263);
        assertEquals(1, SpatialDistanceSupport.resolve(1, SpatialDistanceUnit.FEET_US, feet).sourceCrsValue(), 1e-9);
        assertEquals(0.999998, SpatialDistanceSupport.resolve(1, SpatialDistanceUnit.FEET, feet).sourceCrsValue(), 1e-9);
        assertEquals(2, SpatialDistanceSupport.resolve(2, SpatialDistanceUnit.SOURCE_CRS_UNIT, feet).sourceCrsValue());
        for (var unit : SpatialDistanceUnit.values()) {
            var r = SpatialDistanceSupport.resolve(1, unit, new CrsReference("EPSG", 4326));
            assertEquals(unit == SpatialDistanceUnit.SOURCE_CRS_UNIT, r.valid());
        }
        assertTrue(Double.isNaN(SpatialDistanceSupport.metresPerConfiguredUnit(SpatialDistanceUnit.SOURCE_CRS_UNIT)));
        assertEquals(1853.248d, SpatialDistanceSupport.metresPerConfiguredUnit(SpatialDistanceUnit.NAUTICAL_MILES_US));
    }

    @Test void angularSourceUnitsAreNotAlwaysDegrees() throws Exception {
        // NTF (Paris) uses grads. SOURCE_CRS_UNIT preserves the supplied coordinate-space value.
        var crs = new CrsReference("EPSG", 4807);
        var decoded = org.geotools.referencing.CRS.decode("EPSG:4807", true);
        var unit = decoded.getCoordinateSystem().getAxis(0).getUnit();
        assertEquals(Math.PI / 200d, org.geotools.measure.Units.getConverterToAny(unit,
                org.geotools.measure.Units.RADIAN).convert(1d), 1e-14);
        var resolved = SpatialDistanceSupport.resolve(0.2, SpatialDistanceUnit.SOURCE_CRS_UNIT, crs);
        assertTrue(resolved.valid());
        assertTrue(resolved.angular());
        assertEquals(0.2, resolved.sourceCrsValue());
        var invalid = SpatialDistanceSupport.resolve(0.2, SpatialDistanceUnit.METERS, crs);
        assertFalse(invalid.valid());
        assertTrue(invalid.error().contains("角度单位"));
        assertFalse(invalid.error().contains("（度）"));
    }
}
