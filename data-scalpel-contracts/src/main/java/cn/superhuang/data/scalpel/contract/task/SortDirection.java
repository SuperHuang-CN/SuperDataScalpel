package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("显式排序方向：ASC 按字段值升序，DESC 按字段值降序；NULL 的位置由独立 nullOrdering 决定。")
public enum SortDirection {
    ASC,
    DESC
}
