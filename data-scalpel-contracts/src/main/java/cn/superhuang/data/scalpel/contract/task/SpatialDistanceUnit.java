package cn.superhuang.data.scalpel.contract.task;

/** Explicit distance units shared by spatial Canvas processors. */
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
