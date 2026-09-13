package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("字符串格式规则来源：PRESET 使用平台固定正则且必须选择 preset；REGEX 使用调用方提供的正则且 preset 必须为空。两种方式都对整个非 NULL 字符串匹配。")
public enum FormatPatternKind {
    PRESET,
    REGEX
}
