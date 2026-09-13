package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间距离单位。SOURCE_CRS_UNIT 表示当前 CRS 第一坐标轴单位；其余为米、千米、国际英尺、国际英里、国际海里、国际码，以及 FEET_US/YARDS_US/MILES_US/NAUTICAL_MILES_US 美国测量制。配置单位仅在 CRS 轴单位可可靠换算时使用；切换枚举不会自动修改已有数值。")
public enum SpatialDistanceUnit {
    SOURCE_CRS_UNIT,
    METERS,
    KILOMETERS,
    FEET,
    MILES,
    NAUTICAL_MILES,
    YARDS,
    FEET_US,
    YARDS_US,
    MILES_US,
    NAUTICAL_MILES_US;

    public int introducedInMinorVersion() {
        return switch (this) {
            case YARDS, FEET_US, YARDS_US, MILES_US, NAUTICAL_MILES_US -> 36;
            default -> 0;
        };
    }
}
