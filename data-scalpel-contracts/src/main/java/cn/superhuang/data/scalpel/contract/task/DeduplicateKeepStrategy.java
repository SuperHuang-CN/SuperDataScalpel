package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("重复组保留策略：ANY 不排序并任意保留一行，orderBy 必须为空；FIRST 按显式排序保留第一行；LAST 同时反转每个排序字段的方向和 NULL 顺序后保留第一行。FIRST/LAST 要求非空 Key 和排序，排序完全并列时结果不保证跨重跑稳定。")
public enum DeduplicateKeepStrategy {
    ANY,
    FIRST,
    LAST
}
