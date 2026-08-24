package cn.superhuang.data.scalpel.contract.task;

/** Shared identity and routing fields for multi-table simple processor operations. */
public interface ProcessorOperation {

    String LEGACY_OPERATION_ID = "00000000-0000-0000-0000-000000000000";

    String operationId();

    String sourceTableName();

    ProcessorOutput output();
}
