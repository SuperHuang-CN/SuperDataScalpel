package cn.superhuang.data.scalpel.business.cartography.validation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** Framework-free deterministic equal-interval break calculation. */
public final class ClassBreakCalculator {

    private ClassBreakCalculator() {
    }

    public static List<BigDecimal> equalInterval(BigDecimal minimum, BigDecimal maximum, int classCount) {
        if (minimum == null || maximum == null || minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("数值范围不合法");
        }
        if (classCount < 3 || classCount > 9) throw new IllegalArgumentException("分级数量必须为 3–9");
        if (minimum.compareTo(maximum) == 0) return List.of();
        BigDecimal interval = maximum.subtract(minimum)
                .divide(BigDecimal.valueOf(classCount), MathContext.DECIMAL128);
        List<BigDecimal> breaks = new ArrayList<>(classCount - 1);
        for (int index = 1; index < classCount; index++) {
            breaks.add(minimum.add(interval.multiply(BigDecimal.valueOf(index), MathContext.DECIMAL128)));
        }
        return List.copyOf(breaks);
    }
}
