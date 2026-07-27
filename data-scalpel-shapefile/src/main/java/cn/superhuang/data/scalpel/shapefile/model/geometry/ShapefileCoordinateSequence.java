package cn.superhuang.data.scalpel.shapefile.model.geometry;

import java.util.Arrays;

/** Immutable, primitive-array-backed coordinate sequence. */
public final class ShapefileCoordinateSequence {
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] m;

    public ShapefileCoordinateSequence(double[] x, double[] y, double[] z, double[] m) {
        if (x == null || y == null || x.length != y.length) {
            throw new IllegalArgumentException("x and y arrays must be non-null and have equal length");
        }
        if (z != null && z.length != x.length) {
            throw new IllegalArgumentException("z array length must match x/y");
        }
        if (m != null && m.length != x.length) {
            throw new IllegalArgumentException("m array length must match x/y");
        }
        this.x = x.clone();
        this.y = y.clone();
        this.z = z == null ? null : z.clone();
        this.m = m == null ? null : m.clone();
    }

    public int size() {
        return x.length;
    }

    public boolean hasZ() {
        return z != null;
    }

    public boolean hasM() {
        return m != null;
    }

    public double x(int index) {
        return x[index];
    }

    public double y(int index) {
        return y[index];
    }

    public double z(int index) {
        if (z == null) {
            throw new IllegalStateException("coordinate sequence has no Z dimension");
        }
        return z[index];
    }

    public double m(int index) {
        if (m == null) {
            throw new IllegalStateException("coordinate sequence has no M dimension");
        }
        return m[index];
    }

    public double[] xValues() {
        return x.clone();
    }

    public double[] yValues() {
        return y.clone();
    }

    public double[] zValues() {
        return z == null ? null : z.clone();
    }

    public double[] mValues() {
        return m == null ? null : m.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof ShapefileCoordinateSequence other
                && Arrays.equals(x, other.x)
                && Arrays.equals(y, other.y)
                && Arrays.equals(z, other.z)
                && Arrays.equals(m, other.m);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(x);
        result = 31 * result + Arrays.hashCode(y);
        result = 31 * result + Arrays.hashCode(z);
        return 31 * result + Arrays.hashCode(m);
    }
}
