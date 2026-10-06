package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialJdbcAxisOrderTest {
    @Test void mysqlBridgeUsesLongitudeLatitudeInBothDirections() {
        var mysql = BuiltInDialects.registry().require("MYSQL");
        assertEquals("ST_AsBinary(`geom`, 'axis-order=long-lat')",
                SpatialJdbcRuntimeSupport.geometryReadExpression(mysql, "`geom`"));
        assertEquals("ST_GeomFromWKB(?, 4326, 'axis-order=long-lat')",
                SpatialJdbcRuntimeSupport.geometryWriteExpression(mysql, 4326));
        var pg = BuiltInDialects.registry().require("POSTGRESQL");
        assertEquals("ST_AsBinary(\"geom\")", SpatialJdbcRuntimeSupport.geometryReadExpression(pg, "\"geom\""));
        assertEquals("ST_GeomFromWKB(?, 4326)", SpatialJdbcRuntimeSupport.geometryWriteExpression(pg, 4326));
    }
}
