package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class SpatialTemporalSupport {

    private SpatialTemporalSupport() {
    }

    static WindowParameters validateAndResolve(
            SpatialTemporalSlicing slicing,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (slicing == null) return null;
        CanvasNodeSupport.required(slicing.timeColumnName(), "请选择时间字段",
                path + ".timeColumnName", issues);
        CanvasNodeSupport.required(slicing.timeZone(), "请输入 IANA 时区",
                path + ".timeZone", issues);
        CanvasNodeSupport.required(slicing.windowStartColumnName(), "请输入窗口开始字段名",
                path + ".windowStartColumnName", issues);
        CanvasNodeSupport.required(slicing.windowEndColumnName(), "请输入窗口结束字段名",
                path + ".windowEndColumnName", issues);
        boolean calendar = slicing.usesCalendar();
        if (slicing.calendar() != null && slicing.calendar().mode() == null)
            issues.error("INVALID_SPATIAL_TEMPORAL_MODE", "请选择固定时长或日历周期", path + ".calendar.mode");
        if (!calendar && (slicing.interval() <= 0 || slicing.intervalUnit() == null)) {
            issues.error("INVALID_SPATIAL_TEMPORAL_INTERVAL", "时间窗口必须是正数并选择单位",
                    path + ".interval");
        }
        if (!calendar && ((slicing.repeatInterval() == null) != (slicing.repeatIntervalUnit() == null))) {
            issues.error("INVALID_SPATIAL_TEMPORAL_REPEAT", "重复间隔的数值和单位必须同时配置或同时为空",
                    path + ".repeatInterval");
        } else if (slicing.repeatInterval() != null && slicing.repeatInterval() <= 0) {
            issues.error("INVALID_SPATIAL_TEMPORAL_REPEAT", "重复间隔必须是正数",
                    path + ".repeatInterval");
        }
        ZoneId zone = null;
        try {
            if (!CanvasNodeSupport.blank(slicing.timeZone())) {
                zone = ZoneId.of(slicing.timeZone());
            }
        } catch (DateTimeException exception) {
            issues.error("INVALID_TIME_ZONE", "时间切片时区必须是有效 IANA Zone ID",
                    path + ".timeZone");
        }
        if (calendar) {
            CalendarTimeWindows windows = CalendarTimeWindows.resolve(slicing, zone, issues, path);
            return windows == null ? null : new WindowParameters(0, 0, 0, windows);
        }
        if (slicing.interval() <= 0 || slicing.intervalUnit() == null
                || slicing.repeatInterval() != null && (slicing.repeatIntervalUnit() == null || slicing.repeatInterval() <= 0)) {
            return null;
        }
        long windowMicros;
        long slideMicros;
        try {
            windowMicros = Math.multiplyExact(slicing.interval(), microsPerUnit(slicing.intervalUnit()));
            slideMicros = slicing.repeatInterval() == null
                    ? windowMicros
                    : Math.multiplyExact(slicing.repeatInterval(),
                    microsPerUnit(slicing.repeatIntervalUnit()));
        } catch (ArithmeticException exception) {
            issues.error("INVALID_SPATIAL_TEMPORAL_INTERVAL", "时间窗口超出支持范围",
                    path + ".interval");
            return null;
        }
        long startOffsetMicros = 0;
        if (!CanvasNodeSupport.blank(slicing.referenceTime()) && zone != null) {
            try {
                Instant reference = parseReference(slicing.referenceTime(), zone);
                startOffsetMicros = Math.floorMod(
                        Math.addExact(Math.multiplyExact(reference.getEpochSecond(), 1_000_000L),
                                reference.getNano() / 1_000),
                        slideMicros);
            } catch (DateTimeException | ArithmeticException exception) {
                issues.error("INVALID_SPATIAL_TEMPORAL_REFERENCE",
                        "参考时间必须是有效的 ISO-8601 时间", path + ".referenceTime");
            }
        }
        return new WindowParameters(windowMicros, slideMicros, startOffsetMicros);
    }

    /** Left-closed/right-open windows. A gap returns NULL and must be removed before scope creation. */
    static Column window(Column time, WindowParameters parameters) {
        if (parameters.slideMicros() <= parameters.windowMicros()) {
            return functions.window(time, parameters.windowDuration(), parameters.slideDuration(), parameters.startTime());
        }
        // Spark requires slide <= duration. Use non-overlapping repeat buckets, then retain only
        // each bucket's active prefix. This preserves the reference offset, including pre-Epoch times.
        Column bucket = functions.window(time, parameters.slideDuration(), parameters.slideDuration(), parameters.startTime());
        Column start = bucket.getField("start");
        Column end = functions.timestamp_micros(functions.unix_micros(start).plus(parameters.windowMicros()));
        return functions.when(time.lt(end), functions.struct(start.alias("start"), end.alias("end")));
    }

    static Dataset<Row> addWindows(Dataset<Row> source, Column time, WindowParameters parameters, String start, String end) {
        var names = new java.util.HashSet<String>();
        for (String name : source.columns()) names.add(name.toLowerCase(java.util.Locale.ROOT));
        names.add(start.toLowerCase(java.util.Locale.ROOT)); names.add(end.toLowerCase(java.util.Locale.ROOT));
        String temporary = "__datascalpel_spatial_time_window";
        while (names.contains(temporary.toLowerCase(java.util.Locale.ROOT))) temporary += "_";
        // Materialize the STRUCT once in the logical plan. Repeating time_window in successive
        // withColumn calls expands overlapping windows twice (a cartesian product of starts/ends).
        boolean calendar = parameters.calendar() != null;
        Column expression;
        if (calendar) {
            var struct = DataTypes.createStructType(new org.apache.spark.sql.types.StructField[]{
                    DataTypes.createStructField("start", DataTypes.LongType, false),
                    DataTypes.createStructField("end", DataTypes.LongType, false)});
            expression = functions.explode(functions.udf(parameters.calendar(), DataTypes.createArrayType(struct, false))
                    .apply(functions.unix_micros(time)));
        } else expression = window(time, parameters);
        Dataset<Row> expanded = source.withColumn(temporary, expression);
        Column value = functions.col(CanvasNodeSupport.quoteIdentifier(temporary));
        return expanded.withColumn(start, calendar ? functions.timestamp_micros(value.getField("start")) : value.getField("start"))
                .withColumn(end, calendar ? functions.timestamp_micros(value.getField("end")) : value.getField("end"))
                .drop(temporary).filter(functions.col(CanvasNodeSupport.quoteIdentifier(start)).isNotNull());
    }

    private static Instant parseReference(String value, ZoneId zone) {
        try {
            return Instant.parse(value);
        } catch (DateTimeException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeException ignoredAgain) {
                return LocalDateTime.parse(value).atZone(zone).toInstant();
            }
        }
    }

    private static long microsPerUnit(SpatialDurationUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> 1_000L;
            case SECONDS -> 1_000_000L;
            case MINUTES -> 60_000_000L;
            case HOURS -> 3_600_000_000L;
            case DAYS -> 86_400_000_000L;
            case WEEKS -> 604_800_000_000L;
        };
    }

    record WindowParameters(long windowMicros, long slideMicros, long startOffsetMicros, CalendarTimeWindows calendar) {
        WindowParameters(long windowMicros, long slideMicros, long startOffsetMicros) {
            this(windowMicros, slideMicros, startOffsetMicros, null);
        }
        String windowDuration() {
            return windowMicros + " microseconds";
        }

        String slideDuration() {
            return slideMicros + " microseconds";
        }

        String startTime() {
            return startOffsetMicros + " microseconds";
        }
    }
}
