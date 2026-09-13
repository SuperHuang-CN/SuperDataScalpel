package cn.superhuang.data.scalpel.business.compute.client;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dispatcher 单项运行依赖的安全就绪检查结果。")
public record DispatcherDependency(
        @Schema(description = "依赖的稳定名称，例如 backend、artifact-storage、kafka 或 kafka-listeners。")
        String name,
        @Schema(description = "就绪状态：UP 表示可用，DOWN 表示存在阻塞问题。")
        String state,
        @Schema(description = "已脱敏的问题摘要；依赖正常或没有补充信息时为空。")
        String detail
) {
}
