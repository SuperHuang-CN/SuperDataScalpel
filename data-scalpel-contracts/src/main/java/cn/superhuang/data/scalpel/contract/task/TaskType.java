package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("任务定义类型；当前仅 CANVAS，表示 definition 使用版本化 CanvasDefinition 节点图契约。")
public enum TaskType {
    CANVAS
}
