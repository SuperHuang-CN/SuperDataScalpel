package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.ApiResource;
import cn.superhuang.data.scalpel.business.datasource.service.HttpApiConfigurationCodec;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "HTTP API 数据源中已登记的只读 JSON 资源")
public record ApiResourceResponse(
        @Schema(description = "资源 UUID") UUID id,
        @Schema(description = "所属 HTTP API 数据源 UUID") UUID dataSourceId,
        @Schema(description = "数据源内唯一资源编码") String code,
        @Schema(description = "资源显示名称") String name,
        @Schema(description = "连接器类型；通用 HTTP 资源通常为 GENERIC_HTTP") String connectorType,
        @Schema(description = "是否允许任务引用并执行该资源") boolean enabled,
        @Schema(description = "业务请求模板；ASYNC_JOB 时这是提交远端任务的请求") HttpApiContracts.RequestTemplate request,
        @Schema(description = "请求签名配置") HttpApiContracts.SigningConfiguration signing,
        @Schema(description = "资源调用模式：SINGLE_REQUEST 单次请求，PAGINATED_REQUEST 分页请求，ASYNC_JOB 提交后轮询异步任务") HttpApiContracts.InvocationType invocationType,
        @Schema(description = "最终结果的分页配置；不分页时返回 type=NONE，ASYNC_JOB 也可为结果请求配置分页") HttpApiContracts.PaginationConfiguration pagination,
        @Schema(description = "异步任务提交后的状态轮询与结果请求配置；非异步资源为空") HttpApiContracts.AsyncJobConfiguration asyncJob,
        @Schema(description = "从最终 JSON 响应定位记录的 JSON Pointer；空字符串表示整个响应。目标为数组时逐项作为记录，对象或标量作为单条记录，缺失或 null 作为零条记录") String recordsPointer,
        @Schema(description = "输出字段定义，决定任务读取后的列名与平台类型") java.util.List<HttpApiContracts.OutputField> outputFields,
        @Schema(description = "执行页数、记录数、响应字节和耗时上限") HttpApiContracts.ExecutionLimits limits,
        @Schema(description = "资源创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "资源最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static ApiResourceResponse from(ApiResource resource) {
        HttpApiContracts.ResourceDefinition definition = HttpApiConfigurationCodec.readResource(
                resource.getDefinitionJson());
        return new ApiResourceResponse(
                resource.getId(), resource.getDataSourceId(), resource.getCode(), resource.getName(),
                resource.getConnectorType(), resource.isEnabled(), definition.request(), definition.signing(),
                definition.invocationType(), definition.pagination(), definition.asyncJob(),
                definition.recordsPointer(), definition.outputFields(), definition.limits(),
                resource.getCreatedAt(), resource.getUpdatedAt()
        );
    }
}
