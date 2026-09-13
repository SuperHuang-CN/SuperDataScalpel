package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "节点相对查询根的位置：UPSTREAM 上游、CURRENT 当前根或 DOWNSTREAM 下游")

public enum LineageGraphNodeSide {
    UPSTREAM,
    CURRENT,
    DOWNSTREAM
}
