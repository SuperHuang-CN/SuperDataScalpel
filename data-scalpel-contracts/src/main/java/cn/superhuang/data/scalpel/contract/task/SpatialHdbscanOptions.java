package cn.superhuang.data.scalpel.contract.task;

/** Explicit diagnostic output names; absent on older definitions and inactive on DBSCAN. */
public record SpatialHdbscanOptions(
        String probabilityColumnName,
        String outlierColumnName,
        String exemplarColumnName,
        String stabilityColumnName
) { }
