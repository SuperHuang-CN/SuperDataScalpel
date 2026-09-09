package cn.superhuang.data.scalpel.contract.task;

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
