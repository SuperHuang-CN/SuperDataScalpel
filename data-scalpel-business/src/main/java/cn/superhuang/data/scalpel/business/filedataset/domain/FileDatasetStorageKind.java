package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Canonical parser-facing storage shape of one uploaded file. */
@Schema(description = "解析器使用的规范存储形态：SINGLE_OBJECT 直接读取一个对象；GDB_DIRECTORY 为安全展开后的 FileGDB 对象前缀；SHAPEFILE_COMPONENT_SET 为校验并物化后的 Shapefile 必需及可选组件集合。")
public enum FileDatasetStorageKind {
    SINGLE_OBJECT,
    GDB_DIRECTORY,
    SHAPEFILE_COMPONENT_SET
}
