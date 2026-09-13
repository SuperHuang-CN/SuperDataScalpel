package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("模型物理表模式：MANAGED 由平台创建和维护物理表，EXTERNAL 绑定已有外部表；写入、同步和生命周期能力随模式不同。")
public enum MetadataModelPhysicalTableMode {
    MANAGED,
    EXTERNAL
}
