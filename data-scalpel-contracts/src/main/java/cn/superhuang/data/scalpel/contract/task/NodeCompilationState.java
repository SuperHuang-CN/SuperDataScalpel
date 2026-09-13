package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("节点编译状态：OK 无问题，WARNING 已形成输出但有非阻断告警，ERROR 节点配置或上游状态阻止形成有效输出。")
public enum NodeCompilationState {
    OK,
    WARNING,
    ERROR
}
