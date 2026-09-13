package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Explicit parsing settings for STRING to DATE or TIMESTAMP casts. */
@JsonClassDescription("STRING 转 DATE 或 TIMESTAMP 的显式 Spark datetime pattern 与时区解释。DATE 只能配置 pattern；TIMESTAMP 必须选择 SOURCE_TIME_ZONE 或 EMBEDDED_OFFSET。解析失败由外层 failureStrategy 决定失败或置 NULL。")
public record StringTemporalParseOptions(
        @JsonPropertyDescription("必填的 Spark datetime pattern，最长 128 个字符，不是正则。DATE 不使用时区符号；TIMESTAMP 的时区符号要求由 zoneMode 决定。")
        String pattern,
        @JsonPropertyDescription("STRING 转 TIMESTAMP 必填；SOURCE_TIME_ZONE 把无偏移墙钟值按 sourceTimeZone 解释并归一为 UTC，EMBEDDED_OFFSET 直接遵从文本中的时区或偏移。STRING 转 DATE 时必须为 NULL。")
        StringTimestampZoneMode zoneMode,
        @JsonPropertyDescription("仅 SOURCE_TIME_ZONE 必填的有效 IANA Zone ID，最长 64 个字符；此时 pattern 禁止未加引号的 X/x/Z/O/V/z。EMBEDDED_OFFSET 和 DATE 时必须为空。")
        String sourceTimeZone
) {
}
