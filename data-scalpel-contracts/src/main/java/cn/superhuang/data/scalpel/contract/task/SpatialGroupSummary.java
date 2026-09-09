package cn.superhuang.data.scalpel.contract.task;

public record SpatialGroupSummary(
        String groupByColumnName,
        boolean includeMinorityMajority,
        boolean includeGroupPercentage,
        String minorityFlagColumnName,
        String majorityFlagColumnName,
        String groupPercentageColumnName
) {
}
