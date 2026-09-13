package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Logical type of a file dataset, independent from one uploaded file's physical format. */
@Schema(description = "文件数据集的固定逻辑类型，决定允许的扩展名、解析参数和多表行为。CSV、TSV、TXT、JSON、JSONL、GEOJSON、GEOJSONL、GEOPARQUET、PARQUET、AVRO、SHP 为单表文件；EXCEL、GDB、GPKG 可从一个文件发现多张逻辑表，其中 EXCEL、GDB、GPKG 每个数据集只允许一个当前物理文件。")
public enum FileDatasetType {
    CSV,
    TSV,
    TXT,
    JSON,
    JSONL,
    GEOJSON,
    GEOJSONL,
    GEOPARQUET,
    GPKG,
    PARQUET,
    AVRO,
    EXCEL,
    GDB,
    SHP
}
