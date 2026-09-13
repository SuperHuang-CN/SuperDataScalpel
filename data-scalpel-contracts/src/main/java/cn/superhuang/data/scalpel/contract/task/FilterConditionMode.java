package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("FILTER 条件来源：STRUCTURED 执行可校验的递归 condition；SQL_EXPRESSION 执行受词法限制且经 Spark Analyzer 验证的 sqlExpression。两套草稿可以同时保存，但只有所选模式参与校验和执行；NULL 按 STRUCTURED 处理。")
public enum FilterConditionMode {
    STRUCTURED,
    SQL_EXPRESSION
}
