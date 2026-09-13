package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** How a STRING value supplies the timezone when it is parsed into a Spark TIMESTAMP. */
@JsonClassDescription("STRING 转 TIMESTAMP 的时区来源：SOURCE_TIME_ZONE 要求 pattern 不含时区符号，并用必填 sourceTimeZone 解释本地墙钟时间；EMBEDDED_OFFSET 要求 pattern 含未加引号的 X/x/Z/O/V/z 且 sourceTimeZone 为空，直接使用字符串自身偏移或时区。")
public enum StringTimestampZoneMode {
    SOURCE_TIME_ZONE,
    EMBEDDED_OFFSET
}
