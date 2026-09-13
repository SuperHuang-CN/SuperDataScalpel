package cn.superhuang.data.scalpel.business.asset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "资产发布生命周期：DRAFT 已登记但不出现在匿名门户；PUBLISHED 已发布且可由匿名门户查询；OFFLINE 已下线并保留治理信息和来源快照。")
public enum AssetStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE
}
