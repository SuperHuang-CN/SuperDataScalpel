package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import org.junit.jupiter.api.Test;

class S3ShapefileConfigurationTest {
    @Test
    void derivesCanonicalCompanionsAndAllowsExplicitOverrides() {
        S3ShapefileLocation location = S3ShapefileLocation
                .fromShpKey(" bucket ", "layers/Roads.SHP")
                .withComponentKey(ShapefileComponent.SHX, "layers/Roads.SHX")
                .withComponentKey(ShapefileComponent.CPG, null);

        assertEquals("bucket", location.bucket());
        assertEquals("layers/Roads.SHP", location.componentKey(ShapefileComponent.SHP).orElseThrow());
        assertEquals("layers/Roads.SHX", location.componentKey(ShapefileComponent.SHX).orElseThrow());
        assertEquals("layers/Roads.dbf", location.componentKey(ShapefileComponent.DBF).orElseThrow());
        assertFalse(location.componentKey(ShapefileComponent.CPG).isPresent());
        assertEquals("layers/Roads.prj", location.componentKey(ShapefileComponent.PRJ).orElseThrow());
    }

    @Test
    void rejectsInvalidLocationsAndDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> S3ShapefileLocation.fromShpKey("", "a.shp"));
        assertThrows(IllegalArgumentException.class, () -> S3ShapefileLocation.fromShpKey("bucket", "a.zip"));
        assertThrows(IllegalArgumentException.class, () -> S3ShapefileLocation
                .fromShpKey("bucket", "a.shp")
                .withComponentKey(ShapefileComponent.DBF, "a.shp"));
        assertThrows(IllegalArgumentException.class, () -> S3ShapefileLocation
                .fromShpKey("bucket", "a.shp")
                .withComponentKey(ShapefileComponent.SHP, null));
    }

    @Test
    void validatesBoundedCacheOptions() {
        assertEquals(1024 * 1024, S3ShapefileOptions.defaults().blockSizeBytes());
        assertThrows(IllegalArgumentException.class, () -> new S3ShapefileOptions(63 * 1024, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3ShapefileOptions(96 * 1024, 192 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3ShapefileOptions(64 * 1024, 65 * 1024));
    }
}
