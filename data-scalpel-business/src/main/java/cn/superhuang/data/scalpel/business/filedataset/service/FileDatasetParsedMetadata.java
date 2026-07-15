package cn.superhuang.data.scalpel.business.filedataset.service;

/** Compact summary persisted with the latest successful file sample parsing. */
public record FileDatasetParsedMetadata(int sampledRecordCount, boolean truncated) {
}
