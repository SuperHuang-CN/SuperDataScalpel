package cn.superhuang.data.scalpel.business.lineage.domain;

public enum LineageWriteMode {
    APPEND,
    FULL_OVERWRITE,
    UPSERT,
    PARTITION_OVERWRITE,
    SNAPSHOT_SYNC,
    CREATE_NEW
}
