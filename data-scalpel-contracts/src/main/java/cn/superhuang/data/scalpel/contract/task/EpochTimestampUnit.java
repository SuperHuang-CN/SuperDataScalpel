package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Unit of a signed Unix-epoch value converted to a Spark TIMESTAMP. */
@JsonClassDescription("Unix Epoch 整数单位：SECONDS、MILLISECONDS 或 MICROSECONDS。仅用于 LONG 与 TIMESTAMP 的双向转换，以及 DATE 按 UTC 00:00:00 转 LONG；不会根据数值位数自动推断，TIMESTAMP_NTZ 不支持。")
public enum EpochTimestampUnit {
    SECONDS,
    MILLISECONDS,
    MICROSECONDS
}
