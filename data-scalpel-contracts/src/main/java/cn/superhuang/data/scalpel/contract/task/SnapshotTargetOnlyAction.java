package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("快照同步对目标独有行的动作：KEEP 保留这些行且删除阈值必须为 NULL；DELETE 把完整来源中缺失的目标行列为候选删除，并在空来源保护、最大删除行数和最大删除比例全部通过后于同一事务中删除。")
public enum SnapshotTargetOnlyAction {
    KEEP,
    DELETE
}
