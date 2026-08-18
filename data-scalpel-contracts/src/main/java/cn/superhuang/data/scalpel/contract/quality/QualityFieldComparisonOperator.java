package cn.superhuang.data.scalpel.contract.quality;

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
