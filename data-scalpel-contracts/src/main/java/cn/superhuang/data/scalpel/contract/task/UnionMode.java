package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Union 合并模式：ALL 保留全部输入行和重复行；DISTINCT 在合并完成后按全部输出字段去重。DISTINCT 不支持 Geometry 字段或 UNBOUNDED 输入。")
public enum UnionMode {
    ALL,
    DISTINCT
}
