package cn.superhuang.data.scalpel.contract.task;

/** How a STRING value supplies the timezone when it is parsed into a Spark TIMESTAMP. */
public enum StringTimestampZoneMode {
    SOURCE_TIME_ZONE,
    EMBEDDED_OFFSET
}
