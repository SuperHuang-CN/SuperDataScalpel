package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("任务执行模式。BATCH 处理有界输入并结束；STREAMING 持续消费无界输入，且所有节点和输出都必须明确支持流处理。")
public enum CanvasExecutionMode {
    BATCH,
    STREAMING
}
