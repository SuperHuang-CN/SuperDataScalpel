package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SnapTracksConfiguration;
import cn.superhuang.data.scalpel.contract.task.SnapTracksDirectionMatching;
import cn.superhuang.data.scalpel.contract.task.SnapTracksLineField;
import cn.superhuang.data.scalpel.contract.task.SnapTracksOutputMode;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import scala.collection.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Schema-only lineage planning plus distributed candidate construction and per-track Viterbi selection. */
final class SnapTracksSupport {
    private static final String POINT_ALIAS = "snap_tracks_points";
    private static final String LINE_ALIAS = "snap_tracks_lines";
    private static final String CHOICE_ALIAS = "snap_tracks_choices";
    private static final String CANDIDATE_ALIAS = "snap_tracks_candidates";
    private static final String OUTPUT_POINT_ALIAS = "snap_tracks_output_points";
    private static final String OUTPUT_LINE_ALIAS = "snap_tracks_output_lines";

    private static final StructType MATCH_TYPE = new StructType()
            .add("snapped", new GeometryUDT(), false)
            .add("distanceMetres", DataTypes.DoubleType, false)
            .add("fraction", DataTypes.DoubleType, false)
            .add("lineLengthMetres", DataTypes.DoubleType, false);
    private static final StructType CHOICE_TYPE = new StructType()
            .add("observationId", DataTypes.LongType, false)
            .add("lineRowId", DataTypes.LongType, true);

    private SnapTracksSupport() {
    }

    static Dataset<Row> schemaPlan(
            Dataset<Row> points,
            Dataset<Row> lines,
            SnapTracksConfiguration configuration
    ) {
        Set<String> occupied = new HashSet<>(List.of(points.columns()));
        occupied.addAll(List.of(lines.columns()));
        String pointMembersName = previewName(occupied, "__datascalpel_snap_point_members");
        String lineMembersName = previewName(occupied, "__datascalpel_snap_line_members");

        List<Column> pointDependencies = new ArrayList<>();
        pointDependencies.add(column(points, configuration.pointGeometryColumnName()));
        for (String trackId : configuration.trackIdColumns()) {
            pointDependencies.add(column(points, trackId));
        }
        pointDependencies.add(column(points, configuration.timeColumnName()));
        for (String orderBy : configuration.orderByColumns()) {
            pointDependencies.add(column(points, orderBy));
        }
        Column pointMembers = functions.collect_list(functions.struct(
                pointDependencies.toArray(Column[]::new))).over(Window.partitionBy());
        Dataset<Row> base = points.filter(functions.lit(false))
                .withColumn(pointMembersName, pointMembers);

        List<Column> lineDependencies = new ArrayList<>();
        lineDependencies.add(column(lines, configuration.lineGeometryColumnName()));
        lineDependencies.add(column(lines, configuration.lineIdColumnName()));
        lineDependencies.add(column(lines, configuration.fromNodeColumnName()));
        lineDependencies.add(column(lines, configuration.toNodeColumnName()));
        if (configuration.directionMatching() != null) {
            lineDependencies.add(column(lines,
                    configuration.directionMatching().directionColumnName()));
        }
        List<String> projectedMembers = new ArrayList<>();
        List<Column> summaries = new ArrayList<>();
        summaries.add(functions.collect_list(functions.struct(
                lineDependencies.toArray(Column[]::new))).alias(lineMembersName));
        for (SnapTracksLineField field : configuration.lineFields()) {
            String name = previewName(occupied, "__datascalpel_snap_line_field_members");
            projectedMembers.add(name);
            summaries.add(functions.collect_list(column(lines, field.sourceColumnName())).alias(name));
        }
        Dataset<Row> lineSummary = lines.filter(functions.lit(false)).agg(
                summaries.getFirst(), summaries.subList(1, summaries.size()).toArray(Column[]::new));
        base = base.crossJoin(lineSummary);
        Column matchingDependencies = functions.struct(
                base.col(CanvasNodeSupport.quoteIdentifier(pointMembersName)),
                base.col(CanvasNodeSupport.quoteIdentifier(lineMembersName)));

        List<Column> projection = new ArrayList<>();
        for (String name : points.columns()) projection.add(column(base, name));
        for (int index = 0; index < configuration.lineFields().size(); index++) {
            SnapTracksLineField field = configuration.lineFields().get(index);
            Column dependencies = functions.struct(
                    matchingDependencies,
                    base.col(CanvasNodeSupport.quoteIdentifier(projectedMembers.get(index))));
            projection.add(previewValue(dependencies,
                            lines.schema().apply(field.sourceColumnName()).dataType())
                    .alias(field.outputColumnName()));
        }
        projection.add(previewValue(matchingDependencies,
                        lines.schema().apply(configuration.lineIdColumnName()).dataType())
                .alias(configuration.matchedLineIdColumnName()));
        projection.add(functions.coalesce(
                        previewValue(matchingDependencies, DataTypes.StringType),
                        functions.lit("U"))
                .alias(configuration.matchStatusColumnName()));
        projection.add(previewValue(matchingDependencies, new GeometryUDT())
                .alias(configuration.snappedGeometryColumnName()));
        projection.add(st_functions.ST_X(column(base, configuration.pointGeometryColumnName()))
                .alias(configuration.originalXColumnName()));
        projection.add(st_functions.ST_Y(column(base, configuration.pointGeometryColumnName()))
                .alias(configuration.originalYColumnName()));
        for (String name : List.of(configuration.matchXColumnName(),
                configuration.matchYColumnName(), configuration.matchDistanceColumnName())) {
            projection.add(previewValue(matchingDependencies, DataTypes.DoubleType).alias(name));
        }
        return base.select(projection.toArray(Column[]::new));
    }

    private static Column previewValue(
            Column dependencies,
            org.apache.spark.sql.types.DataType type
    ) {
        return functions.udf((UDF1<Object, Object>) ignored -> {
            throw new IllegalArgumentException("SNAP_TRACKS_PREVIEW_NOT_EXECUTABLE");
        }, type).apply(dependencies);
    }

    private static String previewName(Set<String> occupied, String base) {
        String value = base;
        while (!occupied.add(value)) value += "_";
        return value;
    }

    static Dataset<Row> run(
            TrackNodeSupport.PreparedTrack prepared,
            Dataset<Row> rawLines,
            SnapTracksConfiguration configuration,
            GeometryTypeDefinition geometryType,
            double searchDistanceMetres,
            double sourceUnitsPerMetre
    ) {
        Set<String> occupied = new HashSet<>(List.of(prepared.dataset().columns()));
        occupied.addAll(List.of(rawLines.columns()));
        Names names = Names.resolve(occupied);
        Dataset<Row> points = checkedPoints(prepared.dataset(), configuration, names)
                .withColumn(names.observationId(), functions.monotonically_increasing_id())
                .withColumn(names.observationOrder(),
                        functions.row_number().over(prepared.orderedWindow()).cast(DataTypes.LongType));
        Dataset<Row> lines = checkedLines(rawLines, configuration, names);
        Dataset<Row> candidates = candidates(points, lines, configuration, geometryType,
                searchDistanceMetres, sourceUnitsPerMetre, names, prepared);
        Dataset<Row> choices = choices(candidates, configuration, searchDistanceMetres,
                sourceUnitsPerMetre, names, prepared);

        Dataset<Row> selectedPoints = points.alias(OUTPUT_POINT_ALIAS).join(
                choices.alias(CHOICE_ALIAS),
                qualified(OUTPUT_POINT_ALIAS, names.observationId()).equalTo(
                        qualified(CHOICE_ALIAS, names.observationId())), "inner");
        Dataset<Row> joined = selectedPoints.join(lines.alias(OUTPUT_LINE_ALIAS),
                qualified(CHOICE_ALIAS, names.lineRowId()).equalTo(
                        qualified(OUTPUT_LINE_ALIAS, names.lineRowId())), "left_outer");
        if (configuration.outputMode() == SnapTracksOutputMode.MATCHED_FEATURES) {
            joined = joined.filter(qualified(CHOICE_ALIAS, names.lineRowId()).isNotNull());
        }

        Column point = qualified(OUTPUT_POINT_ALIAS, configuration.pointGeometryColumnName());
        Column line = qualified(OUTPUT_LINE_ALIAS, configuration.lineGeometryColumnName());
        Column match = match(point, line, configuration.distanceMethod(), sourceUnitsPerMetre);
        Column matched = qualified(CHOICE_ALIAS, names.lineRowId()).isNotNull();
        List<Column> projection = new ArrayList<>();
        for (String name : prepared.source().dataset().columns()) {
            projection.add(qualified(OUTPUT_POINT_ALIAS, name));
        }
        for (var field : configuration.lineFields()) {
            projection.add(qualified(OUTPUT_LINE_ALIAS, field.sourceColumnName())
                    .alias(field.outputColumnName()));
        }
        projection.add(qualified(OUTPUT_LINE_ALIAS, configuration.lineIdColumnName())
                .alias(configuration.matchedLineIdColumnName()));
        projection.add(functions.when(matched, functions.lit("M")).otherwise(functions.lit("U"))
                .alias(configuration.matchStatusColumnName()));
        projection.add(functions.when(matched, match.getField("snapped")).otherwise(point)
                .alias(configuration.snappedGeometryColumnName()));
        projection.add(st_functions.ST_X(point).alias(configuration.originalXColumnName()));
        projection.add(st_functions.ST_Y(point).alias(configuration.originalYColumnName()));
        projection.add(functions.when(matched, st_functions.ST_X(match.getField("snapped")))
                .alias(configuration.matchXColumnName()));
        projection.add(functions.when(matched, st_functions.ST_Y(match.getField("snapped")))
                .alias(configuration.matchYColumnName()));
        projection.add(functions.when(matched, match.getField("distanceMetres"))
                .alias(configuration.matchDistanceColumnName()));
        return joined.select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> checkedPoints(
            Dataset<Row> source,
            SnapTracksConfiguration configuration,
            Names names
    ) {
        Column checked = functions.udf((UDF1<Geometry, Geometry>) SnapTracksSupport::checkedPoint,
                source.schema().apply(configuration.pointGeometryColumnName()).dataType())
                .apply(column(source, configuration.pointGeometryColumnName()));
        Dataset<Row> result = source.withColumn(configuration.pointGeometryColumnName(), checked);
        return result.filter(column(result, configuration.timeColumnName()).isNotNull());
    }

    private static Dataset<Row> checkedLines(
            Dataset<Row> source,
            SnapTracksConfiguration configuration,
            Names names
    ) {
        Column geometry = functions.udf((UDF1<Geometry, Geometry>) SnapTracksSupport::checkedLine,
                source.schema().apply(configuration.lineGeometryColumnName()).dataType())
                .apply(column(source, configuration.lineGeometryColumnName()));
        Dataset<Row> checked = source.withColumn(configuration.lineGeometryColumnName(), geometry)
                .withColumn(names.lineRowId(), functions.monotonically_increasing_id());
        Column lineId = column(checked, configuration.lineIdColumnName());
        WindowSpec byLineId = Window.partitionBy(lineId);
        Column duplicateCount = functions.count(functions.lit(1L)).over(byLineId);
        checked = checked.withColumn(configuration.lineIdColumnName(),
                functions.when(lineId.isNull(),
                                functions.raise_error(functions.lit("SNAP_TRACKS_LINE_ID_INVALID")))
                        .when(duplicateCount.gt(1),
                                functions.raise_error(functions.lit("SNAP_TRACKS_LINE_ID_DUPLICATE")))
                        .otherwise(lineId));
        checked = checked.withColumn(configuration.fromNodeColumnName(), requiredNode(
                column(checked, configuration.fromNodeColumnName())))
                .withColumn(configuration.toNodeColumnName(), requiredNode(
                        column(checked, configuration.toNodeColumnName())));
        Column stableKey = column(checked, configuration.lineIdColumnName()).cast("string");
        checked = checked.withColumn(names.stableLineKey(), stableKey)
                .withColumn(names.direction(), direction(configuration, checked));
        return checked;
    }

    private static Column requiredNode(Column value) {
        return functions.when(value.isNull(),
                        functions.raise_error(functions.lit("SNAP_TRACKS_NETWORK_NODE_INVALID")))
                .otherwise(value);
    }

    private static Column direction(SnapTracksConfiguration configuration, Dataset<Row> lines) {
        SnapTracksDirectionMatching matching = configuration.directionMatching();
        if (matching == null) return functions.lit(SnapTracksMapMatcher.Direction.BOTH.name());
        Column value = column(lines, matching.directionColumnName()).cast("string");
        return functions.when(value.eqNullSafe(functions.lit(matching.forwardValue())),
                        functions.lit(SnapTracksMapMatcher.Direction.FORWARD.name()))
                .when(value.eqNullSafe(functions.lit(matching.backwardValue())),
                        functions.lit(SnapTracksMapMatcher.Direction.BACKWARD.name()))
                .when(value.eqNullSafe(functions.lit(matching.bothValue())),
                        functions.lit(SnapTracksMapMatcher.Direction.BOTH.name()))
                .when(value.eqNullSafe(functions.lit(matching.noneValue())),
                        functions.lit(SnapTracksMapMatcher.Direction.NONE.name()))
                .otherwise(functions.lit(SnapTracksMapMatcher.Direction.NONE.name()));
    }

    private static Dataset<Row> candidates(
            Dataset<Row> rawPoints,
            Dataset<Row> rawLines,
            SnapTracksConfiguration configuration,
            GeometryTypeDefinition geometryType,
            double searchDistanceMetres,
            double sourceUnitsPerMetre,
            Names names,
            TrackNodeSupport.PreparedTrack prepared
    ) {
        Dataset<Row> points = rawPoints.alias(POINT_ALIAS);
        Dataset<Row> lines = rawLines.alias(LINE_ALIAS);
        SpatialJoinNearSupport.PreparedGeodesicJoin geodesic = null;
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                configuration.pointGeometryColumnName(), configuration.lineGeometryColumnName(),
                configuration.distanceMethod(), configuration.searchDistance(),
                configuration.searchDistanceUnit());
        Column relation;
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            geodesic = SpatialJoinNearSupport.prepareGeodesicJoin(
                    near, points, lines, POINT_ALIAS, LINE_ALIAS);
            points = geodesic.left();
            lines = geodesic.right();
            relation = SpatialJoinNearSupport.expression(near, points, lines, geometryType, geodesic);
        } else {
            double sourceThreshold = searchDistanceMetres * sourceUnitsPerMetre;
            relation = st_predicates.ST_DWithin(
                    qualified(POINT_ALIAS, configuration.pointGeometryColumnName()),
                    qualified(LINE_ALIAS, configuration.lineGeometryColumnName()),
                    functions.lit(sourceThreshold), functions.lit(false));
        }
        Dataset<Row> joined = points.join(lines, relation, "left_outer");
        Column match = match(
                qualified(POINT_ALIAS, configuration.pointGeometryColumnName()),
                qualified(LINE_ALIAS, configuration.lineGeometryColumnName()),
                configuration.distanceMethod(), sourceUnitsPerMetre);
        List<Column> projection = new ArrayList<>();
        for (var id : prepared.trackIdSchemas()) {
            projection.add(qualified(POINT_ALIAS, id.name()));
        }
        projection.add(qualified(POINT_ALIAS, prepared.segmentColumnName()));
        projection.add(qualified(POINT_ALIAS, names.observationId()));
        projection.add(qualified(POINT_ALIAS, names.observationOrder()));
        projection.add(qualified(POINT_ALIAS, configuration.pointGeometryColumnName())
                .alias(names.observationPoint()));
        projection.add(qualified(LINE_ALIAS, names.lineRowId()));
        projection.add(qualified(LINE_ALIAS, names.stableLineKey()));
        projection.add(qualified(LINE_ALIAS, configuration.fromNodeColumnName())
                .alias(names.fromNode()));
        projection.add(qualified(LINE_ALIAS, configuration.toNodeColumnName())
                .alias(names.toNode()));
        projection.add(qualified(LINE_ALIAS, names.direction()));
        projection.add(match.getField("fraction").alias(names.fraction()));
        projection.add(match.getField("lineLengthMetres").alias(names.lineLengthMetres()));
        projection.add(match.getField("distanceMetres").alias(names.distanceMetres()));
        Dataset<Row> result = joined.select(projection.toArray(Column[]::new));
        Column candidateCount = functions.count(column(result, names.lineRowId()))
                .over(Window.partitionBy(column(result, names.observationId())));
        return result.withColumn(names.lineRowId(),
                functions.when(candidateCount.leq(SnapTracksConfiguration.MAX_CANDIDATES_PER_OBSERVATION),
                                column(result, names.lineRowId()))
                        .otherwise(functions.raise_error(
                                functions.lit("SNAP_TRACKS_CANDIDATE_COUNT_EXCEEDED"))));
    }

    private static Dataset<Row> choices(
            Dataset<Row> candidates,
            SnapTracksConfiguration configuration,
            double searchDistanceMetres,
            double sourceUnitsPerMetre,
            Names names,
            TrackNodeSupport.PreparedTrack prepared
    ) {
        Column candidateRows = functions.collect_list(functions.struct(
                column(candidates, names.observationId()),
                column(candidates, names.observationOrder()),
                column(candidates, names.observationPoint()),
                column(candidates, names.lineRowId()),
                column(candidates, names.stableLineKey()),
                column(candidates, names.fromNode()),
                column(candidates, names.toNode()),
                column(candidates, names.direction()),
                column(candidates, names.fraction()),
                column(candidates, names.lineLengthMetres()),
                column(candidates, names.distanceMetres()))).alias(names.candidateRows());
        List<Column> groups = new ArrayList<>();
        for (var id : prepared.trackIdSchemas()) groups.add(column(candidates, id.name()));
        groups.add(column(candidates, prepared.segmentColumnName()));
        Dataset<Row> grouped = candidates.groupBy(groups.toArray(Column[]::new)).agg(candidateRows);
        SnapTracksMapMatcher matcher = new SnapTracksMapMatcher(
                configuration.distanceMethod(), searchDistanceMetres, sourceUnitsPerMetre);
        Column selected = functions.udf((UDF1<Seq<Row>, List<Row>>) values -> {
            Map<Long, ObservationBuilder> observations = new LinkedHashMap<>();
            for (Row row : CollectionConverters.asJava(values)) {
                long observationId = row.getLong(0);
                long order = row.getLong(1);
                Point point = row.getAs(2);
                ObservationBuilder observation = observations.computeIfAbsent(observationId,
                        ignored -> new ObservationBuilder(observationId, order, point));
                if (!row.isNullAt(3)) {
                    observation.candidates().add(new SnapTracksMapMatcher.Candidate(
                            row.getLong(3), row.getString(4), row.get(5), row.get(6),
                            SnapTracksMapMatcher.Direction.valueOf(row.getString(7)),
                            row.getDouble(8), row.getDouble(9), row.getDouble(10)));
                }
            }
            List<SnapTracksMapMatcher.Observation> input = observations.values().stream()
                    .sorted(Comparator.comparingLong(ObservationBuilder::order))
                    .map(ObservationBuilder::build).toList();
            return matcher.match(input).stream()
                    .map(choice -> RowFactory.create(choice.observationId(), choice.lineRowId()))
                    .toList();
        }, DataTypes.createArrayType(CHOICE_TYPE, false)).apply(column(grouped, names.candidateRows()));
        Dataset<Row> exploded = grouped.withColumn(names.choice(), functions.explode(selected));
        return exploded
                .select(
                        column(exploded, names.choice()).getField("observationId")
                                .alias(names.observationId()),
                        column(exploded, names.choice()).getField("lineRowId")
                                .alias(names.lineRowId()));
    }

    private static Column match(
            Column point,
            Column line,
            SpatialDistanceMethod method,
            double sourceUnitsPerMetre
    ) {
        return functions.udf((UDF2<Geometry, Geometry, Row>) (pointGeometry, lineGeometry) -> {
            if (lineGeometry == null) return null;
            if (!(pointGeometry instanceof Point pointValue)
                    || !(lineGeometry instanceof LineString lineValue)) {
                throw new IllegalArgumentException("SNAP_TRACKS_MATCH_GEOMETRY_INVALID");
            }
            SnapTracksGeometryMatch.Match value = SnapTracksGeometryMatch.solve(
                    pointValue, lineValue, method, sourceUnitsPerMetre);
            return value == null ? null : RowFactory.create(
                    value.snappedPoint(), value.distanceMetres(),
                    value.fraction(), value.lineLengthMetres());
        }, MATCH_TYPE).apply(point, line);
    }

    private static Geometry checkedPoint(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return geometry;
        if (!(geometry instanceof Point point) || !point.isValid()
                || !finite(point.getCoordinate())) {
            throw new IllegalArgumentException("SNAP_TRACKS_POINT_GEOMETRY_INVALID");
        }
        return geometry;
    }

    private static Geometry checkedLine(Geometry geometry) {
        if (!(geometry instanceof LineString line) || line.isEmpty() || !line.isValid()
                || line.getNumPoints() < 2) {
            throw new IllegalArgumentException("SNAP_TRACKS_LINE_GEOMETRY_INVALID");
        }
        for (Coordinate coordinate : line.getCoordinates()) {
            if (!finite(coordinate)) {
                throw new IllegalArgumentException("SNAP_TRACKS_LINE_GEOMETRY_INVALID");
            }
        }
        return geometry;
    }

    private static boolean finite(Coordinate coordinate) {
        return coordinate != null && Double.isFinite(coordinate.x) && Double.isFinite(coordinate.y);
    }

    private static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }

    private static Column qualified(String alias, String name) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(name));
    }

    private record ObservationBuilder(
            long observationId,
            long order,
            Point point,
            List<SnapTracksMapMatcher.Candidate> candidates
    ) {
        ObservationBuilder(long observationId, long order, Point point) {
            this(observationId, order, point, new ArrayList<>());
        }

        SnapTracksMapMatcher.Observation build() {
            return new SnapTracksMapMatcher.Observation(observationId, order, point, candidates);
        }
    }

    private record Names(
            String observationId,
            String observationOrder,
            String observationPoint,
            String lineRowId,
            String stableLineKey,
            String direction,
            String fromNode,
            String toNode,
            String fraction,
            String lineLengthMetres,
            String distanceMetres,
            String candidateRows,
            String choice
    ) {
        static Names resolve(Set<String> occupied) {
            return new Names(
                    next(occupied, "__datascalpel_snap_observation_id"),
                    next(occupied, "__datascalpel_snap_observation_order"),
                    next(occupied, "__datascalpel_snap_observation_point"),
                    next(occupied, "__datascalpel_snap_line_row_id"),
                    next(occupied, "__datascalpel_snap_line_key"),
                    next(occupied, "__datascalpel_snap_direction"),
                    next(occupied, "__datascalpel_snap_from_node"),
                    next(occupied, "__datascalpel_snap_to_node"),
                    next(occupied, "__datascalpel_snap_fraction"),
                    next(occupied, "__datascalpel_snap_line_length"),
                    next(occupied, "__datascalpel_snap_distance"),
                    next(occupied, "__datascalpel_snap_candidates"),
                    next(occupied, "__datascalpel_snap_choice"));
        }

        private static String next(Set<String> occupied, String base) {
            String value = base;
            while (!occupied.add(value)) value += "_";
            return value;
        }
    }
}
