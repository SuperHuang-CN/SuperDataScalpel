package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.task.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Canvas 任务当前保存内容及三类独立信息：任务定义内容版本 version、Canvas JSON 协议版本 schemaVersion/schemaMinorVersion、服务端加载兼容状态 loadStatus。")
public record CanvasTaskDefinitionResponse(
        @Schema(description = "所属 Canvas 任务 UUID。") UUID taskId,
        @Schema(description = "是否已有持久化 Canvas 定义；false 时 definition 是供新建使用的空白当前协议定义，并不表示数据库已有内容。") boolean configured,
        @Schema(description = "任务定义内容版本。未配置时为 0，首次保存为 1，后续内容实际变化时递增；与 Canvas JSON 协议版本无关。") int version,
        @Schema(description = "装载状态：UNCONFIGURED 尚未保存；LOADED 已直接加载或兼容升级到当前协议；INCOMPATIBLE 无法由当前服务端安全读取。") CanvasDefinitionLoadStatus loadStatus,
        @Schema(description = "configured=true 时为持久化定义声明的 Canvas 协议主版本；未配置时返回服务端当前主版本，供客户端创建新定义。") Integer schemaVersion,
        @Schema(description = "configured=true 时为持久化定义声明的 Canvas 协议次版本；未配置时返回服务端当前次版本，供客户端创建新定义。") Integer schemaMinorVersion,
        @Schema(description = "可编辑的完整 Canvas 定义。UNCONFIGURED 时为空白当前协议定义，LOADED 时为已加载并可能内存升级后的定义，INCOMPATIBLE 时为空。") CanvasDefinition definition,
        @Schema(description = "loadStatus=INCOMPATIBLE 时说明协议版本或内容不能安全加载的原因；其他状态为空。") String message,
        @Schema(description = "持久化定义最后更新时间，ISO-8601 UTC 时间戳；UNCONFIGURED 时为空。") Instant updatedAt
) {

    public static CanvasTaskDefinitionResponse unconfigured(UUID taskId) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                false,
                0,
                CanvasDefinitionLoadStatus.UNCONFIGURED,
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                CanvasDefinition.empty(),
                null,
                null
        );
    }

    public static CanvasTaskDefinitionResponse loaded(
            UUID taskId,
            int version,
            int schemaVersion,
            int schemaMinorVersion,
            CanvasDefinition definition,
            Instant updatedAt
    ) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                true,
                version,
                CanvasDefinitionLoadStatus.LOADED,
                schemaVersion,
                schemaMinorVersion,
                definition,
                null,
                updatedAt
        );
    }

    public static CanvasTaskDefinitionResponse incompatible(
            UUID taskId,
            int version,
            int schemaVersion,
            int schemaMinorVersion,
            String message,
            Instant updatedAt
    ) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                true,
                version,
                CanvasDefinitionLoadStatus.INCOMPATIBLE,
                schemaVersion,
                schemaMinorVersion,
                null,
                message,
                updatedAt
        );
    }
}
