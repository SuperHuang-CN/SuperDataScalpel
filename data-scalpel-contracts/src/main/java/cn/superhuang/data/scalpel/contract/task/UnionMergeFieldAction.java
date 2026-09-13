package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 4.62 起 Union/Merge Layers 的合并层字段动作：MATCH 写入已有输出字段，RENAME 以新名称追加输出字段，REMOVE 排除字段。")
public enum UnionMergeFieldAction {
    MATCH,
    RENAME,
    REMOVE
}
