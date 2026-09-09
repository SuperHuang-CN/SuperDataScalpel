package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialWithinStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialWithinStatisticKind;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

/** Quantity treatment and weight expressions for the single Summarize Within operator. */
final class WithinStatisticSupport {
    private WithinStatisticSupport() {}

    static void validate(SpatialWithinStatistic statistic, GeometryKind geometry,
                         String path, CanvasNodeIssueSink issues) {
        if (!statistic.apportionsTotal() && !statistic.usesGeographicWeight()) return;
        if (geometry != GeometryKind.LINESTRING && geometry != GeometryKind.MULTILINESTRING
                && geometry != GeometryKind.POLYGON && geometry != GeometryKind.MULTIPOLYGON) {
            issues.error("SPATIAL_WITHIN_SHAPE_WEIGHT_UNSUPPORTED",
                    "形状分摊和加权要求线或面要素；点使用原值统计", path);
        }
        if (statistic.apportionsTotal() && statistic.usesGeographicWeight()) {
            issues.error("SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED",
                    "暂不支持分摊后再次加权；请选择总量分摊或原值加权", path);
        }
        if (statistic.usesGeographicWeight() && statistic.kind() != SpatialWithinStatisticKind.MEAN
                && statistic.kind() != SpatialWithinStatisticKind.VARIANCE && statistic.kind() != SpatialWithinStatisticKind.STDDEV) {
            issues.error("SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED",
                    "交叠比例加权支持 MEAN、VARIANCE 和 STDDEV", path + ".weighting");
        }
        if (statistic.apportionsTotal() && switch (statistic.kind()) {
            case COUNT, COUNT_FIELD, ANY, LENGTH_WITHIN, AREA_WITHIN -> true;
            default -> false;
        }) {
            issues.error("SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED",
                    "计数、字符串与形状统计不使用总量分摊", path + ".valueTreatment");
        }
    }

    static Column fraction(Column whole, Column intersection, GeometryKind kind,
                           SpatialDistanceMethod method) {
        boolean area = kind == GeometryKind.POLYGON || kind == GeometryKind.MULTIPOLYGON;
        Column denominator = measure(whole, area, method);
        Column numerator = measure(intersection, area, method);
        Column ratio = functions.try_divide(numerator, denominator);
        // A degenerate source has no defined allocation ratio. Zero overlap has a valid zero ratio.
        return functions.when(finite(denominator).and(denominator.gt(0))
                        .and(finite(numerator)).and(numerator.geq(0)),
                functions.greatest(functions.lit(0d), functions.least(functions.lit(1d), ratio)));
    }

    static Column weightedMean(Column value, Column weight) {
        Column valid = finite(value).and(finite(weight)).and(weight.gt(0));
        return functions.try_divide(
                functions.sum(functions.when(valid, value.multiply(weight))),
                functions.sum(functions.when(valid, weight)));
    }

    static Column finiteValue(Column value) {
        Column number = value.cast("double");
        return functions.when(finite(number), number);
    }

    private static Column finite(Column value) {
        return value.isNotNull().and(functions.not(functions.isnan(value)))
                .and(functions.abs(value).notEqual(Double.POSITIVE_INFINITY));
    }

    private static Column measure(Column geometry, boolean area, SpatialDistanceMethod method) {
        if (area) return method == SpatialDistanceMethod.GEODESIC
                ? st_functions.ST_AreaSpheroid(geometry) : st_functions.ST_Area(geometry);
        return method == SpatialDistanceMethod.GEODESIC
                ? st_functions.ST_LengthSpheroid(geometry) : st_functions.ST_Length(geometry);
    }
}
