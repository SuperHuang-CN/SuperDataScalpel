package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("DROP_ROW 的 SQL NULL 组合方式：ANY_NULL 在任一所选字段为 NULL 时删除该行；ALL_NULL 仅在所有所选字段都为 NULL 时删除该行。")
public enum NullMatchMode {
    ANY_NULL,
    ALL_NULL
}
