package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "在 HTTP API 数据源下登记一个可复用的只读 JSON 资源")
public record CreateApiResourceRequest(
        @Schema(description = "数据源内唯一资源编码；以字母开头，只能包含字母、数字和下划线", example = "customer_list")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "资源显示名称")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "连接器类型；通用 HTTP 资源通常省略或使用 GENERIC_HTTP")
        @Size(max = 100) String connectorType,
        @Schema(description = "是否允许任务引用和执行此资源；省略时默认为 true")
        Boolean enabled,
        @Schema(description = "单次业务请求模板，包括方法、相对路径、查询参数、请求头和可选请求体")
        @NotNull @Valid HttpApiContracts.RequestTemplate request,
        @Schema(description = "请求签名配置；无签名时可省略或使用 NONE")
        @Valid HttpApiContracts.SigningConfiguration signing,
        @Schema(description = "资源调用模式：单次请求、分页请求或异步任务")
        @NotNull HttpApiContracts.InvocationType invocationType,
        @Schema(description = "最终结果的分页配置；PAGINATED_REQUEST 必须配置非 NONE 策略，ASYNC_JOB 可配置其结果请求的分页，SINGLE_REQUEST 必须省略或使用 NONE")
        @Valid HttpApiContracts.PaginationConfiguration pagination,
        @Schema(description = "异步任务提交、状态轮询和结果请求配置；仅 ASYNC_JOB 使用，结果请求当前必填")
        @Valid HttpApiContracts.AsyncJobConfiguration asyncJob,
        @Schema(description = "从最终 JSON 响应定位记录数组的 JSON Pointer；根数组使用空字符串", example = "/data/items")
        @NotNull @Size(max = 1000) String recordsPointer,
        @Schema(description = "输出字段定义；每项声明结果列名、JSON 指针、平台类型、可空性和说明")
        @NotEmpty @Size(max = 500) List<HttpApiContracts.OutputField> outputFields,
        @Schema(description = "执行安全上限，用于限制页数、行数、响应字节和总耗时")
        @NotNull @Valid HttpApiContracts.ExecutionLimits limits
) {
}
