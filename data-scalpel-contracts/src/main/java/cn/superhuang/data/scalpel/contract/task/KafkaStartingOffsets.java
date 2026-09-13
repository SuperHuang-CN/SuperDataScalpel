package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Kafka 首次启动位置：EARLIEST 使用 Topic 当前保留的最早 Offset，LATEST 使用启动时末尾；只在没有可恢复 Spark Checkpoint 时生效。")
public enum KafkaStartingOffsets {
    EARLIEST,
    LATEST
}
