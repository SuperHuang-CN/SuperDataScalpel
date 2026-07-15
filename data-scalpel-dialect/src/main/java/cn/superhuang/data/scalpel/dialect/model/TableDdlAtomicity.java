package cn.superhuang.data.scalpel.dialect.model;

/** Atomicity boundary provided by the target database for a generated execution option. */
public enum TableDdlAtomicity {
    NOT_APPLICABLE,
    TRANSACTIONAL_BATCH,
    ATOMIC_SINGLE_STATEMENT,
    NON_TRANSACTIONAL_SEQUENCE
}
