package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Actual physical format of one uploaded file. */
@Schema(description = "上传文件确认后的物理格式；EXCEL 逻辑类型会具体记录为 XLS 或 XLSX，其余值对应同名逻辑类型。GDB、SHP 表示 ZIP 归档内的 FileGDB 或 Shapefile 组件集。")
public enum FileDatasetFormat {

    CSV,
    TSV,
    TXT,
    JSON,
    JSONL,
    GEOJSON,
    GEOJSONL,
    GEOPARQUET,
    GPKG,
    XLS,
    XLSX,
    PARQUET,
    AVRO,
    GDB,
    SHP
}
