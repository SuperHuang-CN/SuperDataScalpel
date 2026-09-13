package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "数据源引用索引的位置类型；当前只有 CANVAS_NODE，表示引用来自已保存 Canvas 节点。JAR 资源绑定和 Local SQL 当前不写入此索引。")
public enum TaskDataSourceReferenceLocationKind {
    CANVAS_NODE
}
