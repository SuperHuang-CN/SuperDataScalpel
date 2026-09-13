package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Top N 的并列处理：EXACT 全局使用 orderBy+limit、分组使用 row_number，每组最多 N 行，边界完全并列时具体保留行可能不稳定；WITH_TIES 使用 rank 并保留 rank<=N，使第 N 名同排序键记录全部保留且结果可能超过 N 行。")
public enum TopNTieStrategy {
    EXACT,
    WITH_TIES
}
