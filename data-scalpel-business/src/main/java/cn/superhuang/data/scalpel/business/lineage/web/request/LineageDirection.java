package cn.superhuang.data.scalpel.business.lineage.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "血缘遍历方向：UPSTREAM 查询上游来源，DOWNSTREAM 查询下游消费者，BOTH 同时查询两侧")

public enum LineageDirection {
    UPSTREAM,
    DOWNSTREAM,
    BOTH;

    public boolean includesUpstream() {
        return this == UPSTREAM || this == BOTH;
    }

    public boolean includesDownstream() {
        return this == DOWNSTREAM || this == BOTH;
    }
}
