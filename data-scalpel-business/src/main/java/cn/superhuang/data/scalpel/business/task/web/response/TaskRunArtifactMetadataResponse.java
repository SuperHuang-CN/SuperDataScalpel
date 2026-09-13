package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
/** Safe metadata used to decide whether a task-run artifact can be read in the browser. */
@Schema(description = "任务运行结果或日志制品的安全元数据，不包含制品正文。")
public record TaskRunArtifactMetadataResponse(
        @Schema(description = "制品种类的稳定标识：result 或 log，可作为预览路径中的 kind。")
        String kind,
        @Schema(description = "文件显示名称。")
        String fileName,
        @Schema(description = "AVAILABLE 表示对象和大小可读取；NOT_GENERATED 表示运行未登记对象或对象不存在；SIZE_UNAVAILABLE 表示已登记对象但读取存储元数据失败。")
        Availability availability,
        @Schema(description = "文件大小，单位字节；无法确定时为空。")
        Long sizeBytes,
        @Schema(description = "制品当前存在且大小不超过 1 MiB 时为 true；false 时仍可能通过下载接口读取。")
        boolean previewAvailable
) {
    @Schema(description = "任务运行制品的生成和读取状态。")
    public enum Availability {
        AVAILABLE,
        NOT_GENERATED,
        SIZE_UNAVAILABLE
    }
}
