package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("文件数据集格式：分隔文本 CSV/TSV/TXT、JSON/JSONL、GeoJSON/GeoJSONL、GeoParquet、GeoPackage、Parquet、Avro、Excel、FileGDB 或 Shapefile；具体文件组合、压缩和读写支持由接口或节点契约限制。")
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
