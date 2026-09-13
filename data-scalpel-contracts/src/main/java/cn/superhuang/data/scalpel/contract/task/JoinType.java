package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("批处理 Join 类型：INNER 只输出匹配组合；LEFT 保留全部左表行；RIGHT 保留全部右表行；FULL 保留两侧全部行。外连接没有匹配记录的一侧以 NULL 补充。")
public enum JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}
