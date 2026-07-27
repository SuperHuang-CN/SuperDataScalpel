package cn.superhuang.data.scalpel.filegdb.model;

/** Public FileGDB field categories supported by the phase-one schema reader. */
public enum FileGdbFieldType {
    INT16,
    INT32,
    FLOAT32,
    FLOAT64,
    STRING,
    TIMESTAMP,
    OID,
    SHAPE,
    BINARY,
    UUID,
    GUID,
    XML
}
