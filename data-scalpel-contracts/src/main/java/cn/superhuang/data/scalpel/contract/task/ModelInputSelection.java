package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** One model selected by a multi-resource model input. */
@JsonClassDescription("模型输入节点选择的一个纳管模型。模型必须已发布、具有字段，并关联到已启用且具有 SOURCE 或 STORAGE 用途的 JDBC 数据源；MANAGED 和 EXTERNAL 模型均可读取。")
public record ModelInputSelection(
        @JsonPropertyDescription("输入模型 UUID 字符串；草稿可为空，编译前必须解析为当前可读模型。输出逻辑表名自动使用模型 code，不能在本项自定义。")
        String modelId
) {
}
