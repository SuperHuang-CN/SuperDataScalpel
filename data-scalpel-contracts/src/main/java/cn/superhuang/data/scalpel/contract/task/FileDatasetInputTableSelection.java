package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** One logical table selected from the input file dataset. */
@JsonClassDescription("文件数据集输入选择的一张已解析逻辑表；来源文件和表必须就绪、Schema 非空，格式须为当前 Task Engine 支持的 CSV、TSV、TXT、JSON、JSONL、GeoJSON、GeoJSONL、GeoParquet、GPKG、Parquet、Avro、Excel、GDB 或 Shapefile。")
public record FileDatasetInputTableSelection(
        @JsonPropertyDescription("文件数据集逻辑表 UUID 字符串；草稿可为空，编译前必须解析为 fileDatasetId 下已完成 Schema 解析的表。输出 Canvas 表名自动使用该表稳定 code。")
        String fileDatasetTableId
) {
}
