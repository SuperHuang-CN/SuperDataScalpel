package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "解析参数多态判别值：CSV 用于 CSV/TSV；TEXT 用于 TXT；JSON、JSON_LINES、GEOJSON、GEOJSONL、GEOPARQUET、GPKG、SPREADSHEET、PARQUET、AVRO、GDB、SHP 分别对应同类文件数据集。")
public enum FileDatasetParsingOptionsKind {
    CSV,
    TEXT,
    JSON,
    JSON_LINES,
    GEOJSON,
    GEOJSONL,
    GEOPARQUET,
    GPKG,
    SPREADSHEET,
    PARQUET,
    AVRO,
    GDB,
    SHP
}
