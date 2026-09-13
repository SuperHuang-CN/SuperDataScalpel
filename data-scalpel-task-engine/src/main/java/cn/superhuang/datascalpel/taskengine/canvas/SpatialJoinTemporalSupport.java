package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalRelationship;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SpatialJoinTemporalSupport {
    private static final String PATH = "configuration.temporalCondition";

    private SpatialJoinTemporalSupport() {
    }

    static void validate(
            SpatialJoinTemporalCondition condition,
            CanvasTableSchema left,
            CanvasTableSchema right,
            CanvasNodeIssueSink issues
    ) {
        if (condition == null) {
            return;
        }
        if (condition.relationship() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择时间关系", PATH + ".relationship");
        }
        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left);
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right);
        List<ResolvedTimeColumn> resolved = new ArrayList<>();
        resolveRequired(leftColumns, condition.leftStartColumnName(),
                PATH + ".leftStartColumnName", "请选择目标表开始或瞬时时间字段", resolved, issues);
        resolveOptional(leftColumns, condition.leftEndColumnName(),
                PATH + ".leftEndColumnName", "目标表结束时间字段", resolved, issues);
        resolveRequired(rightColumns, condition.rightStartColumnName(),
                PATH + ".rightStartColumnName", "请选择连接表开始或瞬时时间字段", resolved, issues);
        resolveOptional(rightColumns, condition.rightEndColumnName(),
                PATH + ".rightEndColumnName", "连接表结束时间字段", resolved, issues);
        PlatformDataType expected = resolved.isEmpty() ? null : resolved.getFirst().column().fieldType();
        if (expected != null) {
            for (ResolvedTimeColumn item : resolved) {
                if (item.column().fieldType() != expected) {
                    issues.error(
                            "SPATIAL_JOIN_TEMPORAL_TYPE_MISMATCH",
                            "时间关系的开始和结束字段必须使用相同类型",
                            item.path()
                    );
                }
            }
        }
        if (condition.usesNearDistance()) {
            if (condition.nearDistance() == null || condition.nearDistance() <= 0) {
                issues.error(
                        "INVALID_SPATIAL_JOIN_TEMPORAL_NEAR_DISTANCE",
                        "时间邻近距离必须是正整数",
                        PATH + ".nearDistance"
                );
            }
            if (condition.nearDistanceUnit() == null) {
                issues.error(
                        "REQUIRED_CONFIGURATION",
                        "请选择时间邻近距离单位",
                        PATH + ".nearDistanceUnit"
                );
            }
            if (condition.nearDistance() != null && condition.nearDistance() > 0
                    && condition.nearDistanceUnit() != null) {
                try {
                    Math.multiplyExact(condition.nearDistance(),
                            microsPerUnit(condition.nearDistanceUnit()));
                } catch (ArithmeticException ignored) {
                    issues.error(
                            "SPATIAL_JOIN_TEMPORAL_NEAR_DISTANCE_OVERFLOW",
                            "时间邻近距离超出可执行范围",
                            PATH + ".nearDistance"
                    );
                }
            }
        }
    }

    static Column expression(
            SpatialJoinTemporalCondition condition,
            Dataset<Row> left,
            Dataset<Row> right
    ) {
        Column leftStart = column(left, condition.leftStartColumnName());
        Column leftEnd = CanvasNodeSupport.blank(condition.leftEndColumnName())
                ? leftStart : column(left, condition.leftEndColumnName());
        Column rightStart = column(right, condition.rightStartColumnName());
        Column rightEnd = CanvasNodeSupport.blank(condition.rightEndColumnName())
                ? rightStart : column(right, condition.rightEndColumnName());
        Column valid = leftStart.isNotNull().and(leftEnd.isNotNull())
                .and(rightStart.isNotNull()).and(rightEnd.isNotNull())
                .and(leftStart.leq(leftEnd)).and(rightStart.leq(rightEnd));
        Column relationship = switch (condition.relationship()) {
            case EQUALS -> leftStart.equalTo(rightStart).and(leftEnd.equalTo(rightEnd));
            case INTERSECTS -> leftStart.leq(rightEnd).and(leftEnd.geq(rightStart));
            case DURING -> leftStart.gt(rightStart).and(leftEnd.lt(rightEnd));
            case CONTAINS -> leftStart.lt(rightStart).and(leftEnd.gt(rightEnd));
            case FINISHES -> leftStart.gt(rightStart).and(leftEnd.equalTo(rightEnd));
            case FINISHED_BY -> leftStart.lt(rightStart).and(leftEnd.equalTo(rightEnd));
            case MEETS -> leftEnd.equalTo(rightStart);
            case MET_BY -> leftStart.equalTo(rightEnd);
            case OVERLAPS -> leftStart.lt(rightStart)
                    .and(leftEnd.gt(rightStart)).and(leftEnd.lt(rightEnd));
            case OVERLAPPED_BY -> leftStart.gt(rightStart)
                    .and(leftStart.lt(rightEnd)).and(leftEnd.gt(rightEnd));
            case STARTS -> leftStart.equalTo(rightStart).and(leftEnd.lt(rightEnd));
            case STARTED_BY -> leftStart.equalTo(rightStart).and(leftEnd.gt(rightEnd));
            case NEAR -> near(leftStart, leftEnd, rightStart, rightEnd, condition);
            case NEAR_BEFORE -> leftEnd.leq(rightStart)
                    .and(leftEnd.plus(interval(condition)).geq(rightStart));
            case NEAR_AFTER -> leftStart.geq(rightEnd)
                    .and(rightEnd.plus(interval(condition)).geq(leftStart));
        };
        return valid.and(relationship);
    }

    static Column gapMicros(
            SpatialJoinTemporalCondition condition,
            Dataset<Row> left,
            Dataset<Row> right
    ) {
        Column leftStart = column(left, condition.leftStartColumnName());
        Column leftEnd = CanvasNodeSupport.blank(condition.leftEndColumnName())
                ? leftStart : column(left, condition.leftEndColumnName());
        Column rightStart = column(right, condition.rightStartColumnName());
        Column rightEnd = CanvasNodeSupport.blank(condition.rightEndColumnName())
                ? rightStart : column(right, condition.rightEndColumnName());
        Column complete = leftStart.isNotNull().and(leftEnd.isNotNull())
                .and(rightStart.isNotNull()).and(rightEnd.isNotNull());
        Column leftBefore = functions.timestamp_diff("MICROSECOND", leftEnd, rightStart);
        Column leftAfter = functions.timestamp_diff("MICROSECOND", rightEnd, leftStart);
        return functions.when(complete,
                functions.greatest(functions.lit(0L), leftBefore, leftAfter));
    }

    static long microsPer(SpatialDurationUnit unit) {
        return microsPerUnit(unit);
    }

    private static Column near(
            Column leftStart,
            Column leftEnd,
            Column rightStart,
            Column rightEnd,
            SpatialJoinTemporalCondition condition
    ) {
        Column intersects = leftStart.leq(rightEnd).and(leftEnd.geq(rightStart));
        Column leftBefore = leftEnd.leq(rightStart)
                .and(leftEnd.plus(interval(condition)).geq(rightStart));
        Column leftAfter = leftStart.geq(rightEnd)
                .and(rightEnd.plus(interval(condition)).geq(leftStart));
        return intersects.or(leftBefore).or(leftAfter);
    }

    private static Column interval(SpatialJoinTemporalCondition condition) {
        long micros = Math.multiplyExact(
                condition.nearDistance(), microsPerUnit(condition.nearDistanceUnit()));
        return functions.expr("INTERVAL " + micros + " MICROSECONDS");
    }

    private static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }

    private static void resolveRequired(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String path,
            String message,
            List<ResolvedTimeColumn> resolved,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) {
            issues.error("REQUIRED_CONFIGURATION", message, path);
            return;
        }
        resolve(columns, name, path, "时间字段", resolved, issues);
    }

    private static void resolveOptional(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String path,
            String label,
            List<ResolvedTimeColumn> resolved,
            CanvasNodeIssueSink issues
    ) {
        if (name == null) {
            return;
        }
        if (name.isBlank()) {
            issues.error("REQUIRED_CONFIGURATION", label + "不能为空字符串", path);
            return;
        }
        resolve(columns, name, path, label, resolved, issues);
    }

    private static void resolve(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String path,
            String label,
            List<ResolvedTimeColumn> resolved,
            CanvasNodeIssueSink issues
    ) {
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + "不存在：" + name, path);
            return;
        }
        if (!temporal(column.fieldType())) {
            issues.error(
                    "TEMPORAL_COLUMN_REQUIRED",
                    label + "必须是 DATE、TIMESTAMP 或 TIMESTAMP_NTZ",
                    path
            );
            return;
        }
        resolved.add(new ResolvedTimeColumn(column, path));
    }

    private static boolean temporal(PlatformDataType type) {
        return type == PlatformDataType.DATE
                || type == PlatformDataType.TIMESTAMP
                || type == PlatformDataType.TIMESTAMP_NTZ;
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

    private record ResolvedTimeColumn(CanvasColumnSchema column, String path) {
    }
}
