package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Physical content compression of one uploaded file dataset. */
@Schema(description = "上传文件的外层压缩方式：NONE 未压缩；GZIP 为受支持单文件格式的 .gz 外层压缩；ZIP 仅作为 FileGDB 或 Shapefile 组件归档容器。")
public enum FileDatasetCompression {

    NONE,
    GZIP,
    ZIP
}
