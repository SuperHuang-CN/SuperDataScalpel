package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = QualityRuleMetric.Violation.class, name = "VIOLATION"),
        @JsonSubTypes.Type(value = QualityRuleMetric.RowCount.class, name = "ROW_COUNT"),
        @JsonSubTypes.Type(value = QualityRuleMetric.Freshness.class, name = "FRESHNESS")
})
public sealed interface QualityRuleMetric permits
        QualityRuleMetric.Violation, QualityRuleMetric.RowCount, QualityRuleMetric.Freshness {
    record Violation(long violationCount, BigDecimal violationPercent,
                     ViolationMetric toleranceMetric, BigDecimal toleranceValue)
            implements QualityRuleMetric {
        public Violation {
            if (violationCount < 0 || violationPercent == null || violationPercent.signum() < 0
                    || violationPercent.compareTo(BigDecimal.valueOf(100)) > 0
                    || toleranceMetric == null || toleranceValue == null || toleranceValue.signum() < 0
                    || toleranceMetric == ViolationMetric.COUNT
                    && toleranceValue.stripTrailingZeros().scale() > 0
                    || toleranceMetric == ViolationMetric.PERCENT
                    && toleranceValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("模型质检异常指标无效");
            }
        }
    }

    record RowCount(long actualRows, long minimumRows) implements QualityRuleMetric {
        public RowCount {
            if (actualRows < 0 || minimumRows < 1) {
                throw new IllegalArgumentException("模型质检行数指标无效");
            }
        }
    }

    record Freshness(Instant maximumValue, Long actualDelayMinutes, long maximumDelayMinutes)
            implements QualityRuleMetric {
        public Freshness {
            if (maximumDelayMinutes < 1 || actualDelayMinutes != null && actualDelayMinutes < 0
                    || (maximumValue == null) != (actualDelayMinutes == null)) {
                throw new IllegalArgumentException("模型质检新鲜度指标无效");
            }
        }
    }
}
