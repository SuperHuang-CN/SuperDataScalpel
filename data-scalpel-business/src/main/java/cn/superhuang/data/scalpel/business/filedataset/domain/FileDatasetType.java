package cn.superhuang.data.scalpel.business.filedataset.domain;

/** Logical type of a file dataset, independent from one uploaded file's physical format. */
public enum FileDatasetType {
    CSV,
    TSV,
    TXT,
    JSON,
    JSONL,
    PARQUET,
    AVRO,
    EXCEL,
    GDB,
    SHP
}
