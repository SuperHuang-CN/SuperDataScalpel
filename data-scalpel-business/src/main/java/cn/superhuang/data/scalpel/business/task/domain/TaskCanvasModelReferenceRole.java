package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "一条 Canvas 节点模型引用的数据流角色：INPUT 读取模型；OUTPUT 写入模型。同一节点和模型同时读写时会分别存在两条不同角色的引用。")
public enum TaskCanvasModelReferenceRole {
    INPUT,
    OUTPUT
}
