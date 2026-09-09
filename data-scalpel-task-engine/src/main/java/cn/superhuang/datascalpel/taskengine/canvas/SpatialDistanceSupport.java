package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialAreaUnit;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.measure.Units;
import org.geotools.referencing.CRS;

import javax.measure.Unit;

/** Deterministic distance conversion for spatial operators that run in source CRS space. */
final class SpatialDistanceSupport {

    // EPSG 9003; do not substitute the international foot (0.3048 m).
    private static final double US_SURVEY_FOOT_METRES = 1200d / 3937d;

    private SpatialDistanceSupport() {
    }

    static Resolution resolve(
            double value,
            SpatialDistanceUnit configuredUnit,
            CrsReference crs
    ) {
        if (configuredUnit == null) {
            return Resolution.error("请选择距离单位");
        }
        CoordinateReferenceSystem decoded;
        try {
            decoded = CRS.decode(crs.authority() + ":" + crs.code(), true);
        } catch (FactoryException | RuntimeException exception) {
            return Resolution.error("无法解析来源 CRS 的轴单位");
        }
        if (decoded.getCoordinateSystem().getDimension() < 1) {
            return Resolution.error("来源 CRS 没有可用的坐标轴");
        }
        Unit<?> axisUnit = decoded.getCoordinateSystem().getAxis(0).getUnit();
        boolean angular = axisUnit != null && axisUnit.isCompatible(Units.RADIAN);
        if (angular) {
            return configuredUnit == SpatialDistanceUnit.SOURCE_CRS_UNIT
                    ? Resolution.angular(value)
                    : Resolution.error("地理 CRS 只允许使用来源 CRS 的角度单位");
        }
        if (configuredUnit == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
            return axisUnit == null
                    ? Resolution.error("无法识别来源 CRS 的轴单位")
                    : Resolution.resolved(value);
        }
        if (axisUnit == null || !axisUnit.isCompatible(Units.METRE)) {
            return Resolution.error("来源 CRS 的轴单位无法进行线性距离换算");
        }
        try {
            double metres = value * metresPerUnit(configuredUnit);
            return Resolution.resolved(Units.getConverterToAny(Units.METRE, axisUnit).convert(metres));
        } catch (RuntimeException exception) {
            return Resolution.error("配置的距离单位无法换算到来源 CRS 轴单位");
        }
    }

    static Resolution sourceUnitsPerConfiguredUnit(
            SpatialDistanceUnit configuredUnit,
            CrsReference crs
    ) {
        return resolve(1d, configuredUnit, crs);
    }

    static double metresPerConfiguredUnit(SpatialDistanceUnit unit) {
        if (unit == null || unit == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
            return Double.NaN;
        }
        return metresPerUnit(unit);
    }

    static double squareMetresPerConfiguredUnit(SpatialAreaUnit unit) {
        if (unit == null) return Double.NaN;
        return switch (unit) {
            case SQUARE_METERS -> 1d;
            case SQUARE_KILOMETERS -> 1_000_000d;
            case HECTARES -> 10_000d;
            case ACRES -> 4_046.8564224d;
            case SQUARE_FEET -> 0.09290304d;
            case SQUARE_MILES -> 2_589_988.110336d;
            case SQUARE_YARDS -> 0.83612736d;
            case SQUARE_FEET_US -> US_SURVEY_FOOT_METRES * US_SURVEY_FOOT_METRES;
            case SQUARE_YARDS_US -> 9d * US_SURVEY_FOOT_METRES * US_SURVEY_FOOT_METRES;
            case SQUARE_MILES_US -> 5280d * 5280d * US_SURVEY_FOOT_METRES * US_SURVEY_FOOT_METRES;
            case ACRES_US -> 43560d * US_SURVEY_FOOT_METRES * US_SURVEY_FOOT_METRES;
        };
    }

    private static double metresPerUnit(SpatialDistanceUnit unit) {
        return switch (unit) {
            case METERS -> 1d;
            case KILOMETERS -> 1_000d;
            case FEET -> 0.3048d;
            case MILES -> 1_609.344d;
            case NAUTICAL_MILES -> 1_852d;
            case YARDS -> 0.9144d;
            case FEET_US -> US_SURVEY_FOOT_METRES;
            case YARDS_US -> 3d * US_SURVEY_FOOT_METRES;
            case MILES_US -> 5280d * US_SURVEY_FOOT_METRES;
            // Esri 109012: US nautical mile before 1954, not an international nautical mile.
            case NAUTICAL_MILES_US -> 1853.248d;
            case SOURCE_CRS_UNIT -> throw new IllegalArgumentException(
                    "SOURCE_CRS_UNIT does not have a fixed metre conversion");
        };
    }

    record Resolution(double sourceCrsValue, boolean angular, String error) {
        static Resolution resolved(double value) {
            return new Resolution(value, false, null);
        }

        static Resolution angular(double value) {
            return new Resolution(value, true, null);
        }

        static Resolution error(String message) {
            return new Resolution(Double.NaN, false, message);
        }

        boolean valid() {
            return error == null && Double.isFinite(sourceCrsValue);
        }
    }
}
