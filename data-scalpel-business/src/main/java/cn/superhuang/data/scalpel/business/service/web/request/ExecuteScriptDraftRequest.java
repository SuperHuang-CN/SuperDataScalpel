package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

@Schema(description = "在指定 DataScalpel Service Engine 和数据源上实际执行一份尚未保存的脚本服务草稿；脚本可能按数据库账号权限产生读写副作用。")

public record ExecuteScriptDraftRequest(
        @Schema(description = "执行脚本草稿的 DataScalpel 服务引擎 UUID。")
        @NotNull UUID engineId,
        @Schema(description = "脚本通过运行时数据库 API 访问的已启用 JDBC 数据源 UUID；必须已在 engineId 对应 Engine 登记为 READY。")
        @NotNull UUID dataSourceId,
        @Schema(description = "模拟调用时提供给脚本的静态服务路由；会转为小写，必须位于 /open-api/v1/ 下，不能以斜杠结尾、包含双斜杠或路径变量。")
        @NotBlank @Size(max = 255) String routePath,
        @Schema(description = "要在 Service Engine Groovy 运行时执行的脚本草稿，最长 500000 字符；当前不是 JVM 安全沙箱，脚本可以调用运行时暴露的数据库函数。")
        @NotBlank @Size(max = 500_000) String script,
        @Schema(description = "模拟调用的任意 JSON 请求体；对象属性可作为脚本变量读取，完整原值同时通过 bodyRoot 提供。")
        Object body,
        @Schema(description = "模拟调用的查询参数映射；null 视为空映射，参数映射及各键会注入脚本上下文。")
        Map<String, Object> query,
        @Schema(description = "仅供脚本读取的模拟请求头映射；null 视为空映射，不参与 Admin 到 Engine 的管理认证。")
        Map<String, String> headers
) {
}
