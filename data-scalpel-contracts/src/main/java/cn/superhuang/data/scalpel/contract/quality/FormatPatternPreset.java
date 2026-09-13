package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("平台预置字符串格式：RESIDENT_ID_CARD 中国居民身份证号码的 15/18 位外形；UNIFIED_SOCIAL_CREDIT_CODE 18 位统一社会信用代码允许字符；MAINLAND_MOBILE_PHONE 11 位中国大陆手机号外形；EMAIL 常见邮箱地址外形；ADMINISTRATIVE_DIVISION_CODE 首位非零的 6 位行政区划代码。预置规则只校验字符串格式，不校验身份证日期/校验位、社会信用代码校验位或行政区划是否真实存在。")
public enum FormatPatternPreset {
    RESIDENT_ID_CARD,
    UNIFIED_SOCIAL_CREDIT_CODE,
    MAINLAND_MOBILE_PHONE,
    EMAIL,
    ADMINISTRATIVE_DIVISION_CODE
}
