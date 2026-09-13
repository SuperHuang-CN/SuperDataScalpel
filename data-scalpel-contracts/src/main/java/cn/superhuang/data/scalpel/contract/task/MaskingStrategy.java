package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("字段脱敏算法：PARTIAL_MASK 保留字符串两端且短值整体掩码；POSITION_MASK 替换从 1 起的单个字符且越界保留原值；KEEP_LENGTH_MASK 等长全掩码；FIXED_VALUE 用固定字符串替换；NULLIFY 把任意可空类型置 NULL。前四种只支持 STRING，所有策略保持输入 NULL。")
public enum MaskingStrategy {
    PARTIAL_MASK,
    POSITION_MASK,
    KEEP_LENGTH_MASK,
    FIXED_VALUE,
    NULLIFY
}
