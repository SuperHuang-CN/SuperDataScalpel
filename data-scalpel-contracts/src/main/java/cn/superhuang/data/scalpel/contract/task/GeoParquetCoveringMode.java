package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("GeoParquet covering 模式：NONE 不添加逐行边界列；ROW_BBOX 添加 {geometryColumnName}_bbox struct 并在 Footer 记录 covering。两种模式都保留每个 part 的整体 bbox 元数据。")
public enum GeoParquetCoveringMode {
    NONE,
    ROW_BBOX
}
