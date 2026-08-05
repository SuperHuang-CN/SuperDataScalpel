package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileDatasetCrsResolverTest {

    @Test
    void resolvesTheOutermostExplicitEpsgIdentifier() {
        var crs = FileDatasetCrsResolver.requireEpsg(
                "PROJCS[\"Web Mercator\",GEOGCS[\"WGS 84\",AUTHORITY[\"EPSG\",\"4326\"]],"
                        + "AUTHORITY[\"EPSG\",\"3857\"]]",
                null,
                "测试图层"
        );

        assertEquals("EPSG", crs.authority());
        assertEquals(3857, crs.code());
    }

    @Test
    void acceptsAConfiguredFallbackWhenWktHasNoIdentifier() {
        assertEquals(4490, FileDatasetCrsResolver.requireEpsg(
                "GEOGCS[\"China Geodetic Coordinate System 2000\"]", 4490, "测试图层"
        ).code());
    }

    @Test
    void treatsTheConfiguredCodeAsFallbackWhenTheFileDeclaresItsOwnCrs() {
        assertEquals(4326, FileDatasetCrsResolver.requireEpsg(
                "GEOGCS[\"WGS 84\",AUTHORITY[\"EPSG\",\"4326\"]]", 4490, "测试图层"
        ).code());
    }
}
