package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "血缘边类型：READS 任务读取资产，WRITES 任务写入资产，DERIVES 字段派生，FIELD_EFFECT 无来源写入影响，EXPOSES 服务暴露模型或字段")

public enum LineageGraphEdgeType {
    READS,
    WRITES,
    DERIVES,
    FIELD_EFFECT,
    EXPOSES
}
