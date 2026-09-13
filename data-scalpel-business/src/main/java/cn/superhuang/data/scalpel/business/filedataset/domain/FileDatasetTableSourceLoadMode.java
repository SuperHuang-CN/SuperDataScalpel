package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件来源写入逻辑表的方式：INITIAL 创建首个来源；APPEND 追加来源；REPLACE_ALL 用新来源替换全部现有来源；REPLACE_SOURCE 只替换 targetSourceId 指定的来源。")
public enum FileDatasetTableSourceLoadMode {
    INITIAL,
    APPEND,
    REPLACE_ALL,
    REPLACE_SOURCE
}
