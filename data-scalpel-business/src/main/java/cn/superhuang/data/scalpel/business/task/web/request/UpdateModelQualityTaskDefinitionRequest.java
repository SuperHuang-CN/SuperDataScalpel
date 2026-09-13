package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(description = "保存 DRAFT 或 DISABLED 模型质检任务的目标模型和失败样本上限；不复制规则定义，创建运行时重新固化目标模型当时的规则与依赖快照。")
public record UpdateModelQualityTaskDefinitionRequest(
        @Schema(description = "待检查的数据模型 UUID，保存时只要求模型存在；任务发布要求至少有一条启用且有效的规则，运行准备还会校验模型、数据源和依赖当前可执行。") @NotNull UUID modelId,
        @Schema(description = "每项规则最多保留的失败样例数，范围 0 到 1000；0 表示不保留样例。创建定义时省略则使用 100，更新已有定义时省略则保留当前值。") @Min(0) @Max(1000) Integer failureSampleLimit
) {
}
