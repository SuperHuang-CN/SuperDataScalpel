package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEntityOfInterest;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEventsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TraceProximityInterestSource;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runtime-only distributed encounter construction and bounded breadth-first propagation. */
final class TraceProximityEventsSupport {
    private static final String LEFT_ALIAS = "trace_proximity_left";
    private static final String RIGHT_ALIAS = "trace_proximity_right";
    private static final String FRONTIER_ALIAS = "trace_proximity_frontier";
    private static final String CONTACT_ALIAS = "trace_proximity_contact";
    private static final String SOURCE_ALIAS = "trace_proximity_source";
    private static final String EVENT_ALIAS = "trace_proximity_event";
    private static final String REACHED_ALIAS = "trace_proximity_reached";

    private TraceProximityEventsSupport() {
    }

    record Result(Dataset<Row> events, Dataset<Row> tracks) {
    }

    static Result schemaPlan(
            Dataset<Row> source,
            Dataset<Row> interestTable,
            TraceProximityEventsConfiguration configuration
    ) {
        Set<String> names = new HashSet<>(List.of(source.columns()));
        if (interestTable != null) names.addAll(List.of(interestTable.columns()));
        String sourceMembersName = nextName(names, "__datascalpel_trace_source_members");
        String interestMembersName = nextName(names, "__datascalpel_trace_interest_members");

        List<Column> sourceDependencies = new ArrayList<>();
        sourceDependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                configuration.pointGeometryColumnName())));
        sourceDependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                configuration.entityIdColumnName())));
        sourceDependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                configuration.timeColumnName())));
        for (String attribute : configuration.attributeMatchColumns()) {
            sourceDependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(attribute)));
        }
        Column sourceMembers = functions.collect_list(functions.struct(
                sourceDependencies.toArray(Column[]::new))).over(Window.partitionBy());
        Dataset<Row> base = source.filter(functions.lit(false))
                .withColumn(sourceMembersName, sourceMembers);

        if (configuration.interestSource() == TraceProximityInterestSource.TABLE) {
            List<Column> interestDependencies = new ArrayList<>();
            interestDependencies.add(interestTable.col(CanvasNodeSupport.quoteIdentifier(
                    configuration.interestEntityIdColumnName())));
            if (!CanvasNodeSupport.blank(configuration.interestStartTimeColumnName())) {
                interestDependencies.add(interestTable.col(CanvasNodeSupport.quoteIdentifier(
                        configuration.interestStartTimeColumnName())));
            }
            Dataset<Row> interestMembers = interestTable.filter(functions.lit(false)).agg(
                    functions.collect_list(functions.struct(
                            interestDependencies.toArray(Column[]::new))).alias(interestMembersName));
            base = base.crossJoin(interestMembers);
        }

        Column dependencies = configuration.interestSource() == TraceProximityInterestSource.TABLE
                ? functions.struct(
                        base.col(CanvasNodeSupport.quoteIdentifier(sourceMembersName)),
                        base.col(CanvasNodeSupport.quoteIdentifier(interestMembersName)))
                : base.col(CanvasNodeSupport.quoteIdentifier(sourceMembersName));
        List<Column> eventProjection = new ArrayList<>(source.columns().length + 5);
        for (String column : source.columns()) {
            eventProjection.add(base.col(CanvasNodeSupport.quoteIdentifier(column)));
        }
        eventProjection.add(previewValue(dependencies, DataTypes.StringType)
                .alias(configuration.fromEntityIdColumnName()));
        eventProjection.add(previewValue(dependencies, DataTypes.StringType)
                .alias(configuration.toEntityIdColumnName()));
        eventProjection.add(previewValue(dependencies, DataTypes.LongType)
                .alias(configuration.depthColumnName()));
        eventProjection.add(previewValue(dependencies, DataTypes.DoubleType)
                .alias(configuration.durationMinutesColumnName()));
        eventProjection.add(previewValue(dependencies, DataTypes.TimestampType)
                .alias(configuration.eventTimeColumnName()));
        Dataset<Row> events = base.select(eventProjection.toArray(Column[]::new));

        Dataset<Row> tracks = null;
        if (configuration.includeTracks()) {
            List<Column> trackProjection = new ArrayList<>(source.columns().length + 1);
            for (String column : source.columns()) {
                trackProjection.add(base.col(CanvasNodeSupport.quoteIdentifier(column)));
            }
            trackProjection.add(previewValue(dependencies, DataTypes.LongType)
                    .alias(configuration.depthColumnName()));
            tracks = base.select(trackProjection.toArray(Column[]::new));
        }
        return new Result(events, tracks);
    }

    private static Column previewValue(Column dependencies, org.apache.spark.sql.types.DataType type) {
        return functions.udf((UDF1<Object, Object>) ignored -> {
            throw new IllegalArgumentException("TRACE_PROXIMITY_PREVIEW_NOT_EXECUTABLE");
        }, type).apply(dependencies);
    }

    private static String nextName(Set<String> names, String base) {
        String name = CanvasSortSupport.temporaryColumnName(names, base);
        names.add(name);
        return name;
    }

    static Result run(
            Dataset<Row> source,
            Dataset<Row> interestTable,
            TraceProximityEventsConfiguration configuration,
            GeometryTypeDefinition geometryType
    ) {
        Names names = Names.resolve(source);
        boolean geodesic = configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Dataset<Row> prepared = prepareSource(source, configuration, geometryType, names, geodesic);
        Dataset<Row> contacts = contacts(prepared, configuration, geometryType, names);
        Dataset<Row> seeds = seeds(source, interestTable, configuration, names).checkpoint();

        Dataset<Row> reached = seeds;
        Dataset<Row> frontier = seeds;
        Dataset<Row> events = contacts.filter(functions.lit(false)).select(
                functions.col(names.fromEntity()),
                functions.col(names.toEntity()),
                functions.col(names.toRowId()),
                functions.col(names.eventTime()),
                functions.col(names.durationMinutes()),
                functions.lit(null).cast("long").alias(names.depth())).checkpoint();

        for (int depth = 1; depth <= configuration.maxTraceDepth(); depth++) {
            Dataset<Row> candidates = frontier.alias(FRONTIER_ALIAS).join(
                            contacts.alias(CONTACT_ALIAS),
                            qualified(FRONTIER_ALIAS, names.entity())
                                    .equalTo(qualified(CONTACT_ALIAS, names.fromEntity()))
                                    .and(qualified(CONTACT_ALIAS, names.eventTime())
                                            .geq(qualified(FRONTIER_ALIAS, names.reachedTime()))),
                            "inner")
                    .select(
                            qualified(CONTACT_ALIAS, names.fromEntity()).alias(names.fromEntity()),
                            qualified(CONTACT_ALIAS, names.toEntity()).alias(names.toEntity()),
                            qualified(CONTACT_ALIAS, names.toRowId()).alias(names.toRowId()),
                            qualified(CONTACT_ALIAS, names.eventTime()).alias(names.eventTime()),
                            qualified(CONTACT_ALIAS, names.durationMinutes())
                                    .alias(names.durationMinutes()),
                            functions.lit((long) depth).alias(names.depth()));

            Dataset<Row> known = reached.select(
                    functions.col(CanvasNodeSupport.quoteIdentifier(names.entity()))
                            .alias(names.knownEntity())).distinct();
            candidates = candidates.alias("trace_proximity_candidate").join(
                    known.alias("trace_proximity_known"),
                    qualified("trace_proximity_candidate", names.toEntity()).equalTo(
                            qualified("trace_proximity_known", names.knownEntity())),
                    "left_anti");
            WindowSpec firstContact = Window.partitionBy(
                            functions.col(CanvasNodeSupport.quoteIdentifier(names.toEntity())))
                    .orderBy(
                            functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())).asc(),
                            functions.col(CanvasNodeSupport.quoteIdentifier(names.fromEntity())).asc(),
                            functions.col(CanvasNodeSupport.quoteIdentifier(names.toRowId())).asc());
            Dataset<Row> nextEvents = candidates
                    .withColumn(names.rank(), functions.row_number().over(firstContact))
                    .filter(functions.col(CanvasNodeSupport.quoteIdentifier(names.rank())).equalTo(1))
                    .drop(names.rank())
                    .checkpoint();
            if (nextEvents.isEmpty()) break;

            events = distributedUnion(events, nextEvents).checkpoint();
            Dataset<Row> nextFrontier = nextEvents.select(
                    functions.col(CanvasNodeSupport.quoteIdentifier(names.toEntity()))
                            .alias(names.entity()),
                    functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime()))
                            .alias(names.reachedTime()),
                    functions.col(CanvasNodeSupport.quoteIdentifier(names.toRowId()))
                            .alias(names.entryRowId()),
                    functions.col(CanvasNodeSupport.quoteIdentifier(names.depth())));
            reached = distributedUnion(reached, nextFrontier).checkpoint();
            frontier = nextFrontier;
        }
        reached = reached.checkpoint();

        Dataset<Row> eventRows = prepared.alias(SOURCE_ALIAS).join(
                events.alias(EVENT_ALIAS),
                qualified(SOURCE_ALIAS, names.rowId())
                        .equalTo(qualified(EVENT_ALIAS, names.toRowId())), "inner");
        List<Column> eventProjection = new ArrayList<>(source.columns().length + 5);
        for (String column : source.columns()) {
            eventProjection.add(qualified(SOURCE_ALIAS, column));
        }
        eventProjection.add(qualified(EVENT_ALIAS, names.fromEntity())
                .alias(configuration.fromEntityIdColumnName()));
        eventProjection.add(qualified(EVENT_ALIAS, names.toEntity())
                .alias(configuration.toEntityIdColumnName()));
        eventProjection.add(qualified(EVENT_ALIAS, names.depth())
                .alias(configuration.depthColumnName()));
        eventProjection.add(qualified(EVENT_ALIAS, names.durationMinutes())
                .alias(configuration.durationMinutesColumnName()));
        eventProjection.add(qualified(EVENT_ALIAS, names.eventTime())
                .alias(configuration.eventTimeColumnName()));
        Dataset<Row> eventOutput = eventRows.select(eventProjection.toArray(Column[]::new)).checkpoint();

        Dataset<Row> trackOutput = null;
        if (configuration.includeTracks()) {
            Dataset<Row> trackRows = prepared.alias(SOURCE_ALIAS).join(
                    reached.alias(REACHED_ALIAS),
                    qualified(SOURCE_ALIAS, configuration.entityIdColumnName())
                            .equalTo(qualified(REACHED_ALIAS, names.entity()))
                            .and(qualified(SOURCE_ALIAS, configuration.timeColumnName())
                                    .geq(qualified(REACHED_ALIAS, names.reachedTime()))
                                    .or(qualified(SOURCE_ALIAS, names.rowId()).equalTo(
                                            qualified(REACHED_ALIAS, names.entryRowId())))),
                    "inner");
            List<Column> trackProjection = new ArrayList<>(source.columns().length + 1);
            for (String column : source.columns()) {
                trackProjection.add(qualified(SOURCE_ALIAS, column));
            }
            trackProjection.add(qualified(REACHED_ALIAS, names.depth())
                    .alias(configuration.depthColumnName()));
            trackOutput = trackRows.select(trackProjection.toArray(Column[]::new)).checkpoint();
        }
        return new Result(eventOutput, trackOutput);
    }

    private static Dataset<Row> prepareSource(
            Dataset<Row> source,
            TraceProximityEventsConfiguration configuration,
            GeometryTypeDefinition geometryType,
            Names names,
            boolean geodesic
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(
                configuration.pointGeometryColumnName()));
        Column checked = functions.udf((UDF1<Geometry, Geometry>) value -> checkedPoint(value, geodesic),
                source.schema().apply(configuration.pointGeometryColumnName()).dataType()).apply(geometry);
        Dataset<Row> prepared = source.withColumn(configuration.pointGeometryColumnName(), checked);
        Column preparedGeometry = prepared.col(CanvasNodeSupport.quoteIdentifier(
                configuration.pointGeometryColumnName()));
        return prepared
                .filter(preparedGeometry.isNotNull())
                .filter(functions.not(st_functions.ST_IsEmpty(preparedGeometry)))
                .filter(prepared.col(CanvasNodeSupport.quoteIdentifier(
                        configuration.entityIdColumnName())).isNotNull())
                .filter(prepared.col(CanvasNodeSupport.quoteIdentifier(
                        configuration.timeColumnName())).isNotNull())
                .withColumn(names.rowId(), functions.monotonically_increasing_id())
                .checkpoint();
    }

    private static Dataset<Row> contacts(
            Dataset<Row> source,
            TraceProximityEventsConfiguration configuration,
            GeometryTypeDefinition geometryType,
            Names names
    ) {
        Dataset<Row> left = detachedAlias(source, LEFT_ALIAS);
        Dataset<Row> right = detachedAlias(source, RIGHT_ALIAS);
        SpatialJoinNearSupport.PreparedGeodesicJoin geodesic = null;
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                configuration.pointGeometryColumnName(), configuration.pointGeometryColumnName(),
                configuration.distanceMethod(), configuration.spatialSearchDistance(),
                configuration.spatialSearchDistanceUnit());
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            geodesic = SpatialJoinNearSupport.prepareGeodesicJoin(
                    near, left, right, LEFT_ALIAS, RIGHT_ALIAS);
            left = geodesic.left();
            right = geodesic.right();
        }

        Column leftRow = qualified(LEFT_ALIAS, names.rowId());
        Column rightRow = qualified(RIGHT_ALIAS, names.rowId());
        Column leftEntity = qualified(LEFT_ALIAS, configuration.entityIdColumnName());
        Column rightEntity = qualified(RIGHT_ALIAS, configuration.entityIdColumnName());
        Column leftTime = qualified(LEFT_ALIAS, configuration.timeColumnName());
        Column rightTime = qualified(RIGHT_ALIAS, configuration.timeColumnName());
        Column relation = leftRow.lt(rightRow).and(leftEntity.notEqual(rightEntity))
                .and(withinTemporal(leftTime, rightTime, configuration));
        for (String attribute : configuration.attributeMatchColumns()) {
            relation = relation.and(qualified(LEFT_ALIAS, attribute)
                    .equalTo(qualified(RIGHT_ALIAS, attribute)));
        }
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            relation = relation.and(SpatialJoinNearSupport.expression(
                    near, left, right, geometryType, geodesic));
        } else {
            double threshold = SpatialDistanceSupport.resolve(
                    configuration.spatialSearchDistance(),
                    configuration.spatialSearchDistanceUnit(), geometryType.crs()).sourceCrsValue();
            relation = relation.and(st_predicates.ST_DWithin(
                    qualified(LEFT_ALIAS, configuration.pointGeometryColumnName()),
                    qualified(RIGHT_ALIAS, configuration.pointGeometryColumnName()),
                    functions.lit(threshold), functions.lit(false)));
        }

        Dataset<Row> pairs = left.join(right, relation, "inner");
        Column eventTime = functions.greatest(leftTime, rightTime);
        Dataset<Row> forward = pairs.select(
                leftEntity.alias(names.fromEntity()),
                rightEntity.alias(names.toEntity()),
                rightRow.alias(names.toRowId()),
                eventTime.alias(names.eventTime()));
        Dataset<Row> reverse = pairs.select(
                rightEntity.alias(names.fromEntity()),
                leftEntity.alias(names.toEntity()),
                leftRow.alias(names.toRowId()),
                eventTime.alias(names.eventTime()));
        Dataset<Row> contacts = forward.unionByName(reverse).dropDuplicates(
                names.fromEntity(), names.toEntity(), names.toRowId(), names.eventTime());

        WindowSpec ordered = Window.partitionBy(
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.fromEntity())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.toEntity())))
                .orderBy(
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())).asc(),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.toRowId())).asc());
        Column previous = functions.lag(functions.col(
                CanvasNodeSupport.quoteIdentifier(names.eventTime())), 1).over(ordered);
        Column newEpisode = functions.when(previous.isNull().or(functions.not(withinTemporal(
                previous,
                functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())),
                configuration))), functions.lit(1L)).otherwise(functions.lit(0L));
        contacts = contacts.withColumn(names.episode(), functions.sum(newEpisode).over(
                ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow())));
        Dataset<Row> episodes = contacts.groupBy(
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.fromEntity())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.toEntity())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.episode())))
                .agg(
                        functions.min(functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())))
                                .alias(names.episodeStart()),
                        functions.max(functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())))
                                .alias(names.episodeEnd()))
                .withColumn(names.durationMinutes(),
                        functions.unix_micros(functions.col(CanvasNodeSupport.quoteIdentifier(
                                        names.episodeEnd())))
                                .minus(functions.unix_micros(functions.col(
                                        CanvasNodeSupport.quoteIdentifier(names.episodeStart()))))
                                .cast("double").divide(functions.lit(60_000_000d)));
        return contacts.join(episodes,
                        new String[]{names.fromEntity(), names.toEntity(), names.episode()}, "inner")
                .select(
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.fromEntity())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.toEntity())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.toRowId())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.eventTime())),
                        functions.col(CanvasNodeSupport.quoteIdentifier(names.durationMinutes())))
                .checkpoint();
    }

    private static Dataset<Row> seeds(
            Dataset<Row> source,
            Dataset<Row> interestTable,
            TraceProximityEventsConfiguration configuration,
            Names names
    ) {
        if (configuration.interestSource() == TraceProximityInterestSource.ENTITY_IDS) {
            List<Row> rows = configuration.entitiesOfInterest().stream().map(value -> RowFactory.create(
                    value.entityId(), new Timestamp(value.startEpochMillis() == null
                            ? 0L : value.startEpochMillis()), null, 0L)).toList();
            StructType schema = new StructType()
                    .add(names.entity(), DataTypes.StringType, false)
                    .add(names.reachedTime(), DataTypes.TimestampType, false)
                    .add(names.entryRowId(), DataTypes.LongType, true)
                    .add(names.depth(), DataTypes.LongType, false);
            return source.sparkSession().createDataFrame(rows, schema);
        }
        Column start = CanvasNodeSupport.blank(configuration.interestStartTimeColumnName())
                ? functions.lit(new Timestamp(0L)).cast("timestamp")
                : functions.coalesce(interestTable.col(CanvasNodeSupport.quoteIdentifier(
                                configuration.interestStartTimeColumnName())),
                        functions.lit(new Timestamp(0L)).cast("timestamp"));
        Dataset<Row> selected = interestTable.select(
                        interestTable.col(CanvasNodeSupport.quoteIdentifier(
                                configuration.interestEntityIdColumnName())).alias(names.entity()),
                        start.alias(names.reachedTime()))
                .filter(functions.col(CanvasNodeSupport.quoteIdentifier(names.entity())).isNotNull())
                .groupBy(functions.col(CanvasNodeSupport.quoteIdentifier(names.entity())))
                .agg(functions.min(functions.col(CanvasNodeSupport.quoteIdentifier(
                        names.reachedTime()))).alias(names.reachedTime()));
        return selected
                .withColumn(names.entryRowId(), functions.lit(null).cast("long"))
                .withColumn(names.depth(), functions.lit(0L));
    }

    private static Column withinTemporal(
            Column first,
            Column second,
            TraceProximityEventsConfiguration configuration
    ) {
        Column interval = temporalInterval(configuration.temporalSearchDistance(),
                configuration.temporalSearchDistanceUnit());
        return first.leq(second.plus(interval)).and(second.leq(first.plus(interval)));
    }

    private static Column temporalInterval(long value, SpatialGroupByProximityTemporalUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> fixedInterval(Math.multiplyExact(value, 1_000L));
            case SECONDS -> fixedInterval(Math.multiplyExact(value, 1_000_000L));
            case MINUTES -> fixedInterval(Math.multiplyExact(value, 60_000_000L));
            case HOURS -> fixedInterval(Math.multiplyExact(value, 3_600_000_000L));
            case DAYS -> fixedInterval(Math.multiplyExact(value, 86_400_000_000L));
            case WEEKS -> fixedInterval(Math.multiplyExact(value, 604_800_000_000L));
            case MONTHS -> functions.expr("INTERVAL " + value + " MONTHS");
            case YEARS -> functions.expr("INTERVAL " + value + " YEARS");
        };
    }

    private static Column fixedInterval(long micros) {
        return functions.expr("INTERVAL " + micros + " MICROSECONDS");
    }

    private static Dataset<Row> detachedAlias(Dataset<Row> source, String alias) {
        String[] columns = source.columns();
        Column[] projection = new Column[columns.length];
        for (int index = 0; index < columns.length; index++) {
            projection[index] = source.col(CanvasNodeSupport.quoteIdentifier(columns[index]))
                    .alias(columns[index]);
        }
        return source.select(projection).alias(alias);
    }

    /**
     * Keeps the breadth-first frontier distributed while avoiding Spark's Union constraint
     * rewrite bug when independently checkpointed iterations carry different expression IDs.
     */
    private static Dataset<Row> distributedUnion(Dataset<Row> first, Dataset<Row> second) {
        return first.sparkSession().createDataFrame(
                first.javaRDD().union(second.javaRDD()), first.schema());
    }

    private static Column qualified(String alias, String column) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(column));
    }

    private static Geometry checkedPoint(Geometry geometry, boolean geodesic) {
        if (geometry == null || geometry.isEmpty()) return geometry;
        if (!(geometry instanceof Point) || !geometry.isValid()) {
            throw new IllegalArgumentException("TRACE_PROXIMITY_GEOMETRY_INVALID");
        }
        for (var coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.getX()) || !Double.isFinite(coordinate.getY())
                    || geodesic && (Math.abs(coordinate.getX()) > 180d
                    || Math.abs(coordinate.getY()) > 90d)) {
                throw new IllegalArgumentException("TRACE_PROXIMITY_GEOMETRY_INVALID");
            }
        }
        return geometry;
    }

    private record Names(
            String rowId,
            String fromEntity,
            String toEntity,
            String toRowId,
            String eventTime,
            String episode,
            String episodeStart,
            String episodeEnd,
            String durationMinutes,
            String entity,
            String reachedTime,
            String entryRowId,
            String depth,
            String knownEntity,
            String rank
    ) {
        static Names resolve(Dataset<Row> source) {
            Set<String> names = new HashSet<>(List.of(source.columns()));
            return new Names(
                    next(names, "__datascalpel_trace_row_id"),
                    next(names, "__datascalpel_trace_from_entity"),
                    next(names, "__datascalpel_trace_to_entity"),
                    next(names, "__datascalpel_trace_to_row_id"),
                    next(names, "__datascalpel_trace_event_time"),
                    next(names, "__datascalpel_trace_episode"),
                    next(names, "__datascalpel_trace_episode_start"),
                    next(names, "__datascalpel_trace_episode_end"),
                    next(names, "__datascalpel_trace_duration_minutes"),
                    next(names, "__datascalpel_trace_entity"),
                    next(names, "__datascalpel_trace_reached_time"),
                    next(names, "__datascalpel_trace_entry_row_id"),
                    next(names, "__datascalpel_trace_depth"),
                    next(names, "__datascalpel_trace_known_entity"),
                    next(names, "__datascalpel_trace_rank"));
        }

        private static String next(Set<String> names, String base) {
            String value = CanvasSortSupport.temporaryColumnName(names, base);
            names.add(value);
            return value;
        }
    }
}
