package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Explicit formatting used when a DATE or timestamp value is converted to STRING. */
@JsonClassDescription("DATE、TIMESTAMP 或 TIMESTAMP_NTZ 转 STRING 的显式格式化设置。TIMESTAMP 先从 UTC 转为 targetTimeZone 的墙钟时间；DATE 和 TIMESTAMP_NTZ 不换时区。pattern 不能输出时区或偏移符号，NULL 输入保持 NULL。")
public record TemporalStringFormatOptions(
        @JsonPropertyDescription("必填的有效日期时间 pattern，最长 128 个字符，不是正则；禁止未加引号的 X/x/Z/O/V/z，以免输出与实际瞬时语义不一致的时区文本。")
        String pattern,
        @JsonPropertyDescription("TIMESTAMP 来源必填的有效 IANA Zone ID，最长 64 个字符；新规则通常使用 UTC。DATE 或 TIMESTAMP_NTZ 来源必须为 NULL，因为两者不进行时区换算。")
        String targetTimeZone
) {
}
