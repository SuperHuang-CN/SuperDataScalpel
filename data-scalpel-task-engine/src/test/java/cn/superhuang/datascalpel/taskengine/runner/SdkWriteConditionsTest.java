package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.dialect.query.QueryFilter;
import cn.superhuang.datascalpel.sdk.WriteCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SdkWriteConditionsTest {
    @Test void preservesFiniteFloatingPointValues() {
        for (Object value : new Object[]{1.25f, 2.5d}) {
            var result = (QueryFilter) SdkWriteConditions.convert(
                    WriteCondition.compare("amount", WriteCondition.Operator.GE, value));
            assertEquals(value, result.values().getFirst());
        }
    }

    @Test void rejectsNonFiniteValuesBeforeAnyWrite() {
        for (Object value : new Object[]{Float.NaN, Float.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SdkWriteConditions.convert(
                    WriteCondition.compare("amount", WriteCondition.Operator.EQ, value)));
        }
    }
}
