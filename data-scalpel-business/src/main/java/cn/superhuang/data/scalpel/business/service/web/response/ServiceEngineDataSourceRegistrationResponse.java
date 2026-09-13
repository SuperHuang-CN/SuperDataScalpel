package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "数据源在指定服务引擎中的注册和同步状态；服务部署依赖该注册就绪。")

public record ServiceEngineDataSourceRegistrationResponse(
        @Schema(description = "服务引擎数据源注册记录 UUID。")
        UUID id,
        @Schema(description = "接收数据源配置的服务引擎 UUID。")
        UUID engineId,
        @Schema(description = "当前关联服务引擎的稳定编码；关联记录异常缺失时为“已删除”。")
        String engineCode,
        @Schema(description = "当前关联服务引擎名称；不是同步时快照，关联记录异常缺失时为“已删除”。")
        String engineName,
        @Schema(description = "数据源 UUID。")
        UUID dataSourceId,
        @Schema(description = "当前平台数据源稳定编码；不是同步时快照，关联记录异常缺失时为“已删除”。")
        String dataSourceCode,
        @Schema(description = "当前平台数据源名称；不是同步时快照，关联记录异常缺失时为“已删除”。")
        String dataSourceName,
        @Schema(description = "当前平台数据源类型编码，例如 POSTGRESQL；不是远端已应用快照的类型。数据源记录异常缺失时为空。")
        String databaseType,
        @Schema(description = "数据源同步状态：PENDING 本次同步已开始但尚未确认；READY 最近同步成功；OUTDATED 曾成功同步但当前配置变化或后续同步失败；FAILED 从未成功同步且最近同步失败。同步调用没有后台队列，返回时通常已进入 READY、OUTDATED 或 FAILED。")
        ServiceEngineDataSourceRegistrationStatus status,
        @Schema(description = "最近一次成功把数据源配置同步到该 Engine 的时间，ISO-8601 UTC 时间戳；从未成功时为空。后续配置过期或同步失败不会清除此历史时间。")
        Instant synchronizedAt,
        @Schema(description = "最近一次同步的安全错误摘要；配置变化仅标记 OUTDATED 时可能为空，READY 时为空。")
        String lastError,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static ServiceEngineDataSourceRegistrationResponse from(
            ServiceEngineDataSourceRegistration registration,
            String engineCode,
            String engineName,
            String dataSourceCode,
            String dataSourceName,
            String databaseType
    ) {
        return new ServiceEngineDataSourceRegistrationResponse(
                registration.getId(), registration.getEngineId(), engineCode, engineName,
                registration.getDataSourceId(), dataSourceCode, dataSourceName, databaseType,
                registration.getStatus(), registration.getSynchronizedAt(), registration.getLastError(),
                registration.getCreatedAt(), registration.getUpdatedAt()
        );
    }
}
