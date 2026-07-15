package cn.superhuang.data.scalpel.dialect.model;

/** Whether a directional type mapping preserves the source semantics. */
public enum TypeMappingQuality {
    EXACT,
    NORMALIZED,
    LOSSY,
    UNSUPPORTED;

    public boolean acceptable() {
        return this == EXACT || this == NORMALIZED;
    }
}
