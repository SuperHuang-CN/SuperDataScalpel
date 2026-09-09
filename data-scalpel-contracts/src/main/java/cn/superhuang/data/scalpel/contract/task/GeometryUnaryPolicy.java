package cn.superhuang.data.scalpel.contract.task;

/** Explicit coordinate handling and checked unary geometry semantics. Null preserves legacy behavior. */
public enum GeometryUnaryPolicy {
    PRESERVE_DIMENSION,
    OUTPUT_XY,
    LEGACY
}
