package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "需要计算引擎的 Spark 运行日志窗口。活动运行从 Dispatcher 获取，终态优先读取归档日志制品；正文可能尚未产生、不可用或只包含尾部片段。LOCAL_SQL 和 WORKFLOW 父运行不适用。")

public record TaskRunLogResponse(
        @Schema(description = "任务运行 UUID。")
        UUID runId,
        @Schema(description = "日志读取状态：WAITING 尚未产生日志；LIVE 为活动运行的 Dispatcher 窗口；ARCHIVING 表示运行已结束但归档对象尚不可读；FINAL 为归档制品预览；UNAVAILABLE 表示没有外部执行或日志来源不可用。")
        Status status,
        @Schema(description = "content 来源：NONE 无正文，DISPATCHER 为运行中日志窗口，ARTIFACT 为终态归档日志制品。")
        Source source,
        @Schema(description = "日志正文窗口；可能为空或只包含末尾片段。")
        String content,
        @Schema(description = "本次日志窗口采集时间。")
        Instant collectedAt,
        @Schema(description = "当前 content 的 UTF-8 字节数；未采集正文时为空，不表示最终日志总大小。")
        Integer windowSizeBytes,
        @Schema(description = "当前 content 是否被 Dispatcher 或 Admin 截断。终态大日志最多返回最近 2,000 行且不超过 1 MiB；true 时应使用下载接口读取完整归档日志。")
        boolean truncated,
        @Schema(description = "日志等待、归档、不可预览或截断原因的可读说明；日志可完整读取时为空。")
        String message,
        @Schema(description = "最终归档日志大小，单位字节；尚未归档时为空。")
        Long finalSizeBytes,
        @Schema(description = "本次响应是否已成功生成最终归档日志的在线预览；只有 status=FINAL 且 content 非空时为 true。")
        boolean finalPreviewAvailable,
        @Schema(description = "是否已确认最终日志对象存在并可通过下载接口读取；当前只在 status=FINAL 时为 true。")
        boolean finalDownloadAvailable
) {
    @Schema(description = "日志窗口状态：WAITING 等待产生日志；LIVE 活动窗口；ARCHIVING 等待终态归档；FINAL 归档预览；UNAVAILABLE 不可用。")
    public enum Status {
        WAITING,
        LIVE,
        ARCHIVING,
        FINAL,
        UNAVAILABLE
    }

    @Schema(description = "当前日志正文的数据来源：NONE 无正文；DISPATCHER 执行器窗口；ARTIFACT 归档对象。")

    public enum Source {
        NONE,
        DISPATCHER,
        ARTIFACT
    }
}
