package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("逐行字段比较符：EQ 等于、NE 不等于、LT 小于、LE 小于等于、GT 大于、GE 大于等于。任一字段为 NULL 的行不参与检查；STRING 和 BOOLEAN 只支持 EQ/NE，数值类型可跨具体数值类型比较，日期时间要求两侧平台类型相同。")
public enum QualityFieldComparisonOperator {
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE;

    public QualityFieldComparisonOperator reverse() {
        return switch (this) {
            case EQ -> EQ;
            case NE -> NE;
            case LT -> GT;
            case LE -> GE;
            case GT -> LT;
            case GE -> LE;
        };
    }
}
