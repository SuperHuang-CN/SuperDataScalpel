package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "资产在一条任务输出流中的角色：INPUT 被任务读取，OUTPUT 被任务写入。")
public enum LineageAssetRole {
    INPUT,
    OUTPUT
}
