package cn.superhuang.data.scalpel.business.asset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "已保存来源快照与当前来源的关系：IN_SYNC 指纹一致或刚同步成功；OUTDATED 来源元数据已变化；SOURCE_UNAVAILABLE 来源存在但当前状态不满足登记条件；SOURCE_MISSING 来源已删除；FAILED 读取或生成快照失败。后三种状态都会保留最近一次成功快照。")
public enum AssetSyncStatus {
    IN_SYNC,
    OUTDATED,
    SOURCE_UNAVAILABLE,
    SOURCE_MISSING,
    FAILED
}
