package cn.superhuang.data.scalpel.contract.task;

/** Optional linked-result strategy. Legacy flattened grouping remains the default when absent. */
public record SpatialWithinGroupResult(
        String areaKeyColumnName,
        String areaKeyOutputColumnName,
        String outputTableName,
        String groupValueColumnName,
        String minorityValueColumnName,
        String majorityValueColumnName,
        String minorityPercentageColumnName,
        String majorityPercentageColumnName,
        SpatialWithinGroupResultMode mode
) {
    public SpatialWithinGroupResult(String areaKeyColumnName, String areaKeyOutputColumnName,
            String outputTableName, String groupValueColumnName, String minorityValueColumnName,
            String majorityValueColumnName, String minorityPercentageColumnName, String majorityPercentageColumnName) {
        this(areaKeyColumnName, areaKeyOutputColumnName, outputTableName, groupValueColumnName,
                minorityValueColumnName, majorityValueColumnName, minorityPercentageColumnName, majorityPercentageColumnName, null);
    }
}
