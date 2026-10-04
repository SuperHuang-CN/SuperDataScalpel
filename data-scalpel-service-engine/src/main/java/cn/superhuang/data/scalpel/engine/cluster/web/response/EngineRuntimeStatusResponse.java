package cn.superhuang.data.scalpel.engine.cluster.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description="当前响应 Engine 节点的同步状态；负载均衡地址每次可能返回不同节点，不代表全部副本")
public record EngineRuntimeStatusResponse(
        @Schema(description="是否启用 PostgreSQL 多副本配置协调；false 时仅为旧单实例模式") boolean enabled,
        @Schema(description="当前进程随机唯一标识，进程重启后变化；非逻辑 Engine 编码") String instanceId,
        @Schema(description="当前节点是否允许新业务请求；未完成同步、同步失败或超过失联阈值时为 false") boolean ready,
        @Schema(description="当前节点完整加载的配置修订号；未完成首次加载时为 -1") long loadedRevision,
        @Schema(description="当前节点最近读到的数据库目标修订号；尚未读取时为 -1，不代表全局实时值") long targetRevision,
        @Schema(description="最近确认本节点配置与数据库一致的 UTC 时间；首次确认前为 null") Instant lastConfirmedAt,
        @Schema(description="安全的同步失败摘要；无已知失败时为 null，具体原因查看节点日志") String error,
        @Schema(description="后台版本轮询间隔，单位毫秒；业务请求不触发轮询") long pollIntervalMs,
        @Schema(description="最长配置未确认时间，单位毫秒；超过后业务返回 503，已知同步失败会提前拒绝") long maxStaleMs
) {}
