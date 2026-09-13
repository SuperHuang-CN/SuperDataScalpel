package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("DERIVE_COLUMNS 白名单函数。TRIM/LTRIM/RTRIM/LOWER/UPPER 恰好 1 个参数；REPLACE/SUBSTRING 恰好 3 个；COALESCE/CONCAT 至少 2 个；DATE_FORMAT、DATE_ADD、DATE_SUB 恰好 2 个。DATE_FORMAT 第二项必须是 STRING Literal，DATE_ADD/DATE_SUB 第二项必须是 BYTE、SHORT、INTEGER 或 LONG Literal。")
public enum DeriveFunction {
    TRIM,
    LTRIM,
    RTRIM,
    LOWER,
    UPPER,
    REPLACE,
    SUBSTRING,
    COALESCE,
    CONCAT,
    DATE_FORMAT,
    DATE_ADD,
    DATE_SUB
}
