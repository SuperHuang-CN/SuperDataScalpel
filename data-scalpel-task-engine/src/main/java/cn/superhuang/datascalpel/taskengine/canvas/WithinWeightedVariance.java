package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Aggregator;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/** Bounded, mergeable weighted central moment; no group materialization or external reads. */
public final class WithinWeightedVariance extends Aggregator<Row, WithinWeightedVariance.Buffer, Double> {
    public static Column expression(Column value, Column weight) {
        var input = DataTypes.createStructType(new org.apache.spark.sql.types.StructField[]{
                DataTypes.createStructField("value", DataTypes.DoubleType, true),
                DataTypes.createStructField("weight", DataTypes.DoubleType, true)});
        return functions.udaf(new WithinWeightedVariance(), Encoders.row(input))
                .apply(value.cast("double"), weight.cast("double"));
    }

    @Override public Buffer zero() { return new Buffer(); }

    @Override public Buffer reduce(Buffer b, Row row) {
        if (row == null || row.isNullAt(0) || row.isNullAt(1)) return b;
        double value = row.getDouble(0), weight = row.getDouble(1);
        if (!Double.isFinite(value) || !Double.isFinite(weight) || weight <= 0) return b;
        if (b.count == 0) {
            b.count = 1; b.weight = weight; b.mean = value;
            return b;
        }
        double combinedWeight = b.weight + weight;
        double delta = value - b.mean;
        double nextMean = b.mean + delta * (weight / combinedWeight);
        b.m2 += weight * delta * (value - nextMean);
        b.mean = nextMean; b.weight = combinedWeight; b.count++;
        return b;
    }

    @Override public Buffer merge(Buffer a, Buffer b) {
        if (b.count == 0) return a;
        if (a.count == 0) {
            a.count = b.count; a.weight = b.weight; a.mean = b.mean; a.m2 = b.m2;
            return a;
        }
        double combinedWeight = a.weight + b.weight;
        double delta = b.mean - a.mean;
        a.m2 += b.m2 + delta * delta * (a.weight / combinedWeight) * b.weight;
        a.mean += delta * (b.weight / combinedWeight);
        a.weight = combinedWeight; a.count += b.count;
        return a;
    }

    @Override public Double finish(Buffer b) {
        if (b.count < 2 || !Double.isFinite(b.weight) || b.weight <= 0) return null;
        // Enterprise 11.3 equation: denominator ((N'-1)/N') * sum(w), not sum(w)-sum(w²)/sum(w).
        double variance = (b.m2 / b.weight) * ((double) b.count / (b.count - 1));
        return Double.isFinite(variance) ? Math.max(0d, variance) : null;
    }

    @Override public Encoder<Buffer> bufferEncoder() { return Encoders.bean(Buffer.class); }
    @Override public Encoder<Double> outputEncoder() { return Encoders.DOUBLE(); }

    /** Spark bean encoder keeps only four scalar state fields per group. */
    public static final class Buffer implements java.io.Serializable {
        private long count;
        private double weight;
        private double mean;
        private double m2;
        public Buffer() { }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public double getMean() { return mean; }
        public void setMean(double mean) { this.mean = mean; }
        public double getM2() { return m2; }
        public void setM2(double m2) { this.m2 = m2; }
    }
}
