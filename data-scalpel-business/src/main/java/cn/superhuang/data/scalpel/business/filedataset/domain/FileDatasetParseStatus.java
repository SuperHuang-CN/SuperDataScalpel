package cn.superhuang.data.scalpel.business.filedataset.domain;

/** Parsing lifecycle reserved for the subsequent metadata-inspection phase. */
public enum FileDatasetParseStatus {

    UNPARSED,
    PARSING,
    READY,
    FAILED
}
