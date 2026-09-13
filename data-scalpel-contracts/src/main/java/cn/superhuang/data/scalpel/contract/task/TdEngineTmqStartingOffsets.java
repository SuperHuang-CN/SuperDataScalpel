package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("TDengine TMQ 无 Checkpoint 时的首次位置：EARLIEST 使用每个 VGroup 当前 beginning Offset；LATEST 使用当前 end Offset。恢复运行后 Spark Checkpoint 是唯一 Offset 事实来源，平台关闭自动提交且不提交服务端 Consumer Group Offset。")
public enum TdEngineTmqStartingOffsets {
    EARLIEST,
    LATEST
}
