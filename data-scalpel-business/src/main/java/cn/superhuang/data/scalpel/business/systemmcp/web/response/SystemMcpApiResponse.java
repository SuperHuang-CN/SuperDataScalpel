package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "系统 MCP 接口目录中的一项代码契约投影")
public record SystemMcpApiResponse(
        @Schema(description = "接口目录记录 UUID，用于管理页面提交开放状态变更") UUID id,
        @Schema(description = "稳定接口标识，格式为 HTTP 方法、一个空格和 Spring MVC 路由模板，例如 GET /api/v1/data-sources/{id}") String operationId,
        @Schema(description = "固定 HTTP 方法，当前为 GET 或 POST") String method,
        @Schema(description = "固定 Spring MVC 路由模板；花括号中的名称对应 pathParams 字段") String path,
        @Schema(description = "接口所属业务模块") String module,
        @Schema(description = "接口中文名称") String summary,
        @Schema(description = "接口用途、限制和执行语义说明") String description,
        @Schema(description = "操作性质：READ 只读查询、WRITE 数据变更、EXECUTE 触发执行；缺少语义声明时为 UNKNOWN 且不能开放") String effect,
        @Schema(description = "目录状态：AVAILABLE 可开放，INCOMPLETE 缺少必要语义声明，UNSUPPORTED 当前传输或契约不受支持，REMOVED 当前部署已移除") String status,
        @Schema(description = "接口不能开放的具体原因；status 为 AVAILABLE 时为空") String unavailableReason,
        @Schema(description = "管理员是否已开放该接口；不可用接口始终不能调用") boolean enabled,
        @Schema(description = "当前请求和响应契约的 SHA-256 指纹；契约变化时改变，尚未生成有效契约时为空") String fingerprint,
        @Schema(description = "目录记录最后更新时间，ISO-8601 UTC 时间") Instant updatedAt,
        @Schema(description = "完整接口契约；列表查询为空，详情查询在契约可生成时返回参数、请求体、响应、本接口用到的局部 Schema、前置条件和关联接口") JsonNode contract
) {
}
