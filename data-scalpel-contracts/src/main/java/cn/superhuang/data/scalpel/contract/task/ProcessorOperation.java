package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Shared identity and routing fields for multi-table simple processor operations. */
@JsonClassDescription("简单多表 Processor 的公共操作视图；每项以唯一 UUID 选择一张进入节点前已存在的来源表，并声明替换来源或创建新表。同一节点内来源表不能重复，任一操作失败会使整个节点无效。")
public interface ProcessorOperation {

    String LEGACY_OPERATION_ID = "00000000-0000-0000-0000-000000000000";

    String operationId();

    String sourceTableName();

    ProcessorOutput output();
}
