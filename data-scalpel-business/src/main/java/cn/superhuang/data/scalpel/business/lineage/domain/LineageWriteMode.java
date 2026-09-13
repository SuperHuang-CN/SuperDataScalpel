package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务写入资产的方式：APPEND 追加，FULL_OVERWRITE 全量覆盖，UPSERT 更新插入，PARTITION_OVERWRITE 分区覆盖，SNAPSHOT_SYNC 快照同步，CREATE_NEW 新建目标。")
public enum LineageWriteMode {
    APPEND,
    FULL_OVERWRITE,
    UPSERT,
    PARTITION_OVERWRITE,
    SNAPSHOT_SYNC,
    CREATE_NEW
}
