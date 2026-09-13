package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 4.61 起的 Dissolve 分组语义。ALL_OR_FIELDS 保持空分组全局融合、非空按字段值融合；CONNECTED_COMPONENTS 在未配置分组字段时按面要素相交或接触关系的传递闭包分别融合。")
public enum SpatialAggregateDissolveGroupingMode {
    ALL_OR_FIELDS,
    CONNECTED_COMPONENTS
}
