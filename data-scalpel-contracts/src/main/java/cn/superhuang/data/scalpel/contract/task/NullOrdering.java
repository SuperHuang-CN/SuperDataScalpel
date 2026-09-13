package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("显式 NULL 排序位置：FIRST 把 NULL 放在所有非 NULL 值之前，LAST 放在所有非 NULL 值之后；与 ASC/DESC 独立配置。")
public enum NullOrdering {
    FIRST,
    LAST
}
