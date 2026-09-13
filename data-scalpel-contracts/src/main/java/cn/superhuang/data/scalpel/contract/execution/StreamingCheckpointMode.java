package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("实时 JAR 启动的 Checkpoint 策略：CONTINUE 复用兼容部署状态继续处理；FRESH 创建隔离的新代次并从资源定义的起点开始。")
public enum StreamingCheckpointMode {
    CONTINUE,
    FRESH
}
