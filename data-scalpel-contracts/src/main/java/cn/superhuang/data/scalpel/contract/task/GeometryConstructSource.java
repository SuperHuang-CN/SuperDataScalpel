package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GeometryConstructSource.Wkt.class, name = "WKT"),
        @JsonSubTypes.Type(value = GeometryConstructSource.Wkb.class, name = "WKB"),
        @JsonSubTypes.Type(value = GeometryConstructSource.GeoJson.class, name = "GEOJSON"),
        @JsonSubTypes.Type(value = GeometryConstructSource.PointFromXy.class, name = "POINT_FROM_XY")
})
public sealed interface GeometryConstructSource permits
        GeometryConstructSource.Wkt,
        GeometryConstructSource.Wkb,
        GeometryConstructSource.GeoJson,
        GeometryConstructSource.PointFromXy {

    record Wkt(String columnName) implements GeometryConstructSource {
    }

    record Wkb(String columnName) implements GeometryConstructSource {
    }

    record GeoJson(String columnName) implements GeometryConstructSource {
    }

    record PointFromXy(
            String xColumnName,
            String yColumnName
    ) implements GeometryConstructSource {
    }
}
