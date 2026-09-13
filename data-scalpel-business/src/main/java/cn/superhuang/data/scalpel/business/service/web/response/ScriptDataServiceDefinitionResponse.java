package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "脚本数据服务的当前完整定义，包含目标数据源、Groovy 脚本和可复用请求示例。")

public record ScriptDataServiceDefinitionResponse(
        @Schema(description = "脚本执行时可访问的数据源 UUID。")
        UUID dataSourceId,
        @Schema(description = "部署后在 DataScalpel Service Engine Groovy 运行时执行的数据服务脚本文本；当前不是 JVM 安全沙箱。")
        String script,
        @Schema(description = "用于测试和说明脚本调用方式的请求示例列表；至少包含一个示例。")
        List<ScriptRequestExampleResponse> examples,
        @Schema(description = "该脚本定义的版本号，初始为 1；数据源、脚本文本或规范化后的请求示例实际变化时递增，重复保存相同定义不变。")
        int version
) {
}
