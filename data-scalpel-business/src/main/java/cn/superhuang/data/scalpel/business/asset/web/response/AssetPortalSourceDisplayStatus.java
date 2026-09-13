package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "详情页本次读取采用的来源：AVAILABLE 当前来源可用并展示实时安全元数据；UNAVAILABLE 成功读取当前来源但其业务状态不满足登记条件，仍展示实时安全元数据；CACHED 当前来源缺失或读取失败，回退最近一次成功快照。")

public enum AssetPortalSourceDisplayStatus {
    AVAILABLE,
    UNAVAILABLE,
    CACHED
}
