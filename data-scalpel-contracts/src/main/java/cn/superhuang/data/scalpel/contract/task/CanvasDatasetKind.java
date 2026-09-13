package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("逻辑数据集有界性。BOUNDED 是可完整处理后结束的有限数据；UNBOUNDED 是持续到达的流数据，时间运算通常还要求事件时间和 Watermark。")
public enum CanvasDatasetKind {
    BOUNDED,
    UNBOUNDED
}
