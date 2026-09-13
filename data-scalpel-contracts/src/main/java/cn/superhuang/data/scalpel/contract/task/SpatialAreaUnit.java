package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间面积输出单位：平方米、平方千米、公顷、国际英亩、国际平方英尺/英里/码，以及美国测量制平方英尺/码/英里和英亩。单位只转换结果显示值，不改变相交比例；地理 CRS 的角度平方不能直接换算为这些面积单位。")
public enum SpatialAreaUnit {
    SQUARE_METERS,
    SQUARE_KILOMETERS,
    HECTARES,
    ACRES,
    SQUARE_FEET,
    SQUARE_MILES,
    SQUARE_YARDS,
    SQUARE_FEET_US,
    SQUARE_YARDS_US,
    SQUARE_MILES_US,
    ACRES_US;

    public int introducedInMinorVersion() {
        return switch (this) {
            case SQUARE_YARDS, SQUARE_FEET_US, SQUARE_YARDS_US, SQUARE_MILES_US, ACRES_US -> 36;
            default -> 0;
        };
    }
}
