package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Preparation state of one uploaded physical file. */
@Schema(description = "物理文件准备状态：PREPARING 正在排队或执行 FileGDB、Shapefile、GeoPackage 的安全物化和表发现；READY 已形成解析器可读取的规范存储。没有失败状态，最终准备失败会移除尚未生效的文件记录。")
public enum FileDatasetFileStatus {
    PREPARING,
    READY
}
