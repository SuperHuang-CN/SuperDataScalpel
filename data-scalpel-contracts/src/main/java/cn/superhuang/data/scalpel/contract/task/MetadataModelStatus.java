package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("编译元数据快照中的模型状态：DRAFT 草稿、PUBLISHED 已发布、DISABLED 已停用；输入输出节点会按自身规则限制可用状态。")
public enum MetadataModelStatus {
    DRAFT,
    PUBLISHED,
    DISABLED
}
