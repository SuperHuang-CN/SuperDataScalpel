package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("GeoParquet 列压缩编码：SNAPPY 或 ZSTD；编码写入每个 Parquet part 的 Footer，文件扩展名均保持 .parquet。")
public enum GeoParquetCompressionCodec {
    SNAPPY,
    ZSTD
}
