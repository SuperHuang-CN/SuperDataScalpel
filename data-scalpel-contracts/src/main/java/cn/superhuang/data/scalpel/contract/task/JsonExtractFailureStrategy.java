package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("JSON 提取失败策略：ERROR 使用严格 parse_json/variant_get，畸形 JSON 或目标类型转换失败时节点失败；SET_NULL 使用 try_parse_json/try_variant_get 并在失败位置返回 SQL NULL。两者在来源为 NULL、Path 不存在或命中 JSON null 时都返回 NULL，不会跳过记录。")
public enum JsonExtractFailureStrategy {
    ERROR,
    SET_NULL
}
