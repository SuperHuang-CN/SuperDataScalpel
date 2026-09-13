package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Stream-Static Join 类型：INNER 只输出匹配静态表的左侧流记录；LEFT 保留全部左侧流记录，未匹配时右侧投影字段为 NULL。当前不支持 RIGHT 或 FULL。")
public enum StreamJoinType {
    INNER,
    LEFT
}
