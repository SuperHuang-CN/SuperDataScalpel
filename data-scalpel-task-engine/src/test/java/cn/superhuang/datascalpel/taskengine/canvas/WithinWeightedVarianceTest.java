package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.RowFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WithinWeightedVarianceTest {
    @Test void followsThePublishedEquationRatherThanTheIncorrectPrintedLineResult() {
        var aggregate = new WithinWeightedVariance();
        var b = aggregate.zero();
        aggregate.reduce(b, RowFactory.create(1000d, 0.5d));
        aggregate.reduce(b, RowFactory.create(600d, 2d / 3));
        assertEquals(3_840_000d / 49, aggregate.finish(b), 1e-8);
        // Reliability-weight correction would give 80000; treating sum(weights) as N gives another result.
        assertNotEquals(80000d, aggregate.finish(b));
        assertNotEquals(1_268_571.4286d, aggregate.finish(b));
    }

    @Test void emptySingletonMissingAndNonpositiveWeightsHaveExplicitSemantics() {
        var a = new WithinWeightedVariance(); var b = a.zero();
        assertNull(a.finish(b));
        for (var row : java.util.List.of(RowFactory.create(null, 1d), RowFactory.create(3d, null),
                RowFactory.create(Double.NaN, 1d), RowFactory.create(Double.POSITIVE_INFINITY, 1d),
                RowFactory.create(3d, 0d), RowFactory.create(3d, -1d), RowFactory.create(3d, Double.NaN))) a.reduce(b, row);
        assertEquals(0, b.getCount()); assertNull(a.finish(b));
        a.reduce(b, RowFactory.create(10d, 0.1d)); assertNull(a.finish(b));
        a.reduce(b, RowFactory.create(10d, 0.5d)); assertEquals(0d, a.finish(b));
    }

    @Test void centralMomentsRemainStableForLargeOffsetsAndMergePartitionStates() {
        var a = new WithinWeightedVariance(); var b = a.zero();
        a.reduce(b, RowFactory.create(1e12, 0.5d)); a.reduce(b, RowFactory.create(1e12 + 2, 0.5d));
        assertEquals(2d, a.finish(b), 1e-9);
        var left = a.zero(); var right = a.zero();
        a.reduce(left, RowFactory.create(1e12, 0.5d)); a.reduce(right, RowFactory.create(1e12 + 2, 0.5d));
        assertEquals(2d, a.finish(a.merge(left, right)), 1e-9);
        assertEquals(2d, a.finish(a.merge(a.zero(), left)), 1e-9);
        assertEquals(2d, a.finish(a.merge(left, a.zero())), 1e-9);
        // Equal positive weights reduce to the usual sample variance, regardless of their common scale.
        var normal = a.zero();
        for (double x : new double[]{1, 2, 3}) a.reduce(normal, RowFactory.create(x, 0.01d));
        assertEquals(1d, a.finish(normal), 1e-12);
    }
}
