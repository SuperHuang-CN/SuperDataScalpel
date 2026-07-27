package cn.superhuang.data.scalpel.business.filedataset.domain;

/** Durable queue lifecycle for one file dataset table parsing attempt group. */
public enum FileDatasetParseJobStatus {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
