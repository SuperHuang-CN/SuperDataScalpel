package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Work kinds sharing the durable file-dataset parsing queue. */
@Schema(description = "文件解析作业类型：FILE_PREPARATION 解包或物化需要预处理的文件，并在成功后创建表校验作业；TABLE_SOURCE_VALIDATE 解析、校验一个文件内来源并提交逻辑表变更。")
public enum FileDatasetParseJobType {
    FILE_PREPARATION,
    TABLE_SOURCE_VALIDATE
}
