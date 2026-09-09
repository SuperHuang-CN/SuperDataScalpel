package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaGeometry.Footprint;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.ArrayList;

/** Private Spark columns for geodesic area reconstruction, not Canvas/Manifest data. */
final class TrackGeodesicAreaColumns {
    private TrackGeodesicAreaColumns() { }

    static Column validObservation(Column shape, DataType geometryType) {
        return functions.udf((UDF1<Geometry, Geometry>) geometry -> {
            if (geometry == null || geometry.isEmpty()) return geometry;
            try {
                TrackGeodesicAreaGeometry.requireCoordinates(geometry);
                if (geometry instanceof org.locationtech.jts.geom.Polygon || geometry instanceof MultiPolygon)
                    TrackGeodesicPolygon.prepareSource(geometry);
                else if (!(geometry instanceof org.locationtech.jts.geom.Point))
                    throw TrackGeodesicAreaGeometry.failure("TRACK_AREA_GEOMETRY_INVALID");
                return geometry;
            } catch (RuntimeException error) { throw TrackGeodesicAreaGeometry.safe(error); }
        }, geometryType).apply(shape);
    }

    static Column exceedsGap(Column current, Column previous, double thresholdMetres) {
        return functions.udf((UDF2<Geometry, Geometry, Boolean>) (left, right) -> {
            Boolean within = Wgs84GeometryDistance.withinDistance(left, right, thresholdMetres);
            return within == null ? null : !within;
        }, DataTypes.BooleanType).apply(current, previous);
    }

    private static StructType footprintType(DataType geometryType) {
        var vertex = new StructType()
                .add("longitude",DataTypes.DoubleType,false)
                .add("latitude",DataTypes.DoubleType,false);
        return new StructType()
                .add("referenceLongitude",DataTypes.DoubleType,false)
                .add("referenceLatitude",DataTypes.DoubleType,false)
                .add("vertices",DataTypes.createArrayType(vertex,false),false)
                .add("area",geometryType,false);
    }

    static Column footprint(Column shape, Column radiusMetres, boolean buffer, double maximumSegmentLength, DataType geometryType) {
        return functions.udf((UDF2<Geometry,Double,Row>) (geometry,radius) -> {
            var footprint = TrackGeodesicAreaGeometry.footprint(geometry,radius,buffer,maximumSegmentLength);
            if (footprint == null) return null;
            Row[] vertices = footprint.hullVertices().stream()
                    .map(vertex -> RowFactory.create(vertex.longitude(),vertex.latitude())).toArray(Row[]::new);
            // An explicit Catalyst struct survives shuffle/aggregation. Geometry.userData does
            // not, and rendering seams must never become candidates for the connection hull.
            return RowFactory.create(footprint.reference().longitude(),footprint.reference().latitude(),vertices,footprint.area());
        },footprintType(geometryType)).apply(shape,radiusMetres.cast(DataTypes.DoubleType));
    }

    static Column connect(Column orderedFootprints, double maximumSegmentLength, DataType geometryType) {
        return functions.udf((UDF1<scala.collection.Seq<Row>,Geometry>) values -> {
            try {
                if (values == null) throw TrackGeodesicAreaGeometry.failure("TRACK_AREA_GEOMETRY_INVALID");
                var observations = new ArrayList<Footprint>();
                var iterator = values.iterator();
                long total = 0;
                while (iterator.hasNext()) {
                    Row row = iterator.next();
                    if (row == null) throw TrackGeodesicAreaGeometry.failure("TRACK_AREA_GEOMETRY_INVALID");
                    scala.collection.Seq<Row> encodedVertices = row.getSeq(2);
                    MultiPolygon area = row.getAs(3);
                    total += encodedVertices.size() + (long) area.getNumPoints();
                    // Bound accumulated decoding as well as the geometry assembly itself.
                    if (total > TrackGeodesicAreaGeometry.MAX_VERTICES)
                        throw TrackGeodesicAreaGeometry.failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
                    var vertices = new ArrayList<Vertex>(encodedVertices.size());
                    var points = encodedVertices.iterator();
                    while (points.hasNext()) {
                        Row point = points.next(); vertices.add(new Vertex(point.getDouble(0),point.getDouble(1)));
                    }
                    observations.add(new Footprint(new Vertex(row.getDouble(0),row.getDouble(1)),vertices,area));
                }
                return TrackGeodesicAreaGeometry.connect(observations,maximumSegmentLength);
            } catch (RuntimeException error) { throw TrackGeodesicAreaGeometry.safe(error); }
        },geometryType).apply(orderedFootprints);
    }
}
