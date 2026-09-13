package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Schema(description = "脚本数据服务的数据源绑定、Groovy 脚本和调用示例；部署后脚本可按数据库账号权限执行读写 SQL。")

public record ScriptDataServiceDefinitionRequest(
        @Schema(description = "脚本通过运行时数据库 API 访问的已启用 JDBC 数据源 UUID；保存时还要求已在目标 Engine 登记为 READY。")
        @NotNull UUID dataSourceId,
        @Schema(description = "部署后在 DataScalpel Service Engine Groovy 运行时执行的脚本，最长 500000 字符；当前不是 JVM 安全沙箱。")
        @NotBlank @Size(max = 500_000) String script,
        @Schema(description = "用于工作台调试和调用说明的完整请求示例，至少 1 个、最多 50 个；示例会随定义整体替换，保存、启用或发布时不会自动执行。")
        @NotNull @Size(min = 1, max = 50) List<@Valid ScriptRequestExampleRequest> examples
) {

    public ScriptDataServiceDefinitionRequest(UUID dataSourceId, String script) {
        this(dataSourceId, script, List.of(defaultExample()));
    }

    public static ScriptRequestExampleRequest defaultExample() {
        return new ScriptRequestExampleRequest(
                "default",
                "默认示例",
                "{\n  \n}",
                List.of(),
                List.of(new ScriptRequestParameterRequest(
                        "default-content-type", "Content-Type", "application/json"
                ))
        );
    }
}
