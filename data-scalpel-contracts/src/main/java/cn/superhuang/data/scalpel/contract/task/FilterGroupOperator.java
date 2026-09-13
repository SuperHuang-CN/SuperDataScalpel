package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("结构化筛选条件组的组合逻辑：AND 要求所有子条件成立；OR 要求至少一个子条件成立。空组不允许。")
public enum FilterGroupOperator {
    AND,
    OR
}
