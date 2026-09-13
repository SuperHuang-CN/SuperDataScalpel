package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("一对一空间连接的结果选择方式。SUMMARIZE_MATCHES 汇总全部匹配连接记录并输出 Join Count；KEEP_ONE 按显式确定性顺序保留一条连接记录。")
public enum SpatialJoinOneToOneMode {
    SUMMARIZE_MATCHES,
    KEEP_ONE
}
