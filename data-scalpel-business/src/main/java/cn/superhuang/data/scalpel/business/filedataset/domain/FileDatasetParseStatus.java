package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Parsing lifecycle reserved for the subsequent metadata-inspection phase. */
@Schema(description = "逻辑表解析状态：WAITING_CRS 新来源待确认空间参考、保留原文件且不允许执行；QUEUED 初始来源等待解析；PARSING 初始来源正在解析；SCHEMA_READY 已有权威 Schema 但格式或安全限制不支持预览；READY 已有权威 Schema 且支持预览。没有 FAILED 状态，初始解析最终失败会删除尚未生效的逻辑表；既有表的数据变更失败则保留原就绪状态和旧来源。")
public enum FileDatasetParseStatus {
    WAITING_CRS,
    QUEUED,
    PARSING,
    SCHEMA_READY,
    READY
}
