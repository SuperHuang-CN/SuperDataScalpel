package cn.superhuang.data.scalpel.business.asset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "门户排序：RECOMMENDED 推荐优先，再按发布时间倒序；LATEST 按发布时间倒序；CREATED 按资产登记创建时间倒序。所有排序均以 UUID 倒序作为同时间的稳定顺序。")
public enum AssetPortalSort {
    RECOMMENDED,
    LATEST,
    CREATED
}
