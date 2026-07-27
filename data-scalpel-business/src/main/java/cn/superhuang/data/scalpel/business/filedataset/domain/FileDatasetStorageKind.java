package cn.superhuang.data.scalpel.business.filedataset.domain;

/** Canonical parser-facing storage shape of one uploaded file. */
public enum FileDatasetStorageKind {
    SINGLE_OBJECT,
    GDB_DIRECTORY,
    SHAPEFILE_COMPONENT_SET
}
