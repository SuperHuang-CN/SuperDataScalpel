package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Read-only test request for an unsaved SQL service definition. */
@Schema(description = "对尚未保存的 SQL 数据服务定义进行只读校验和受限预览。")
public record SqlServiceTestRequest(
        @Schema(description = "本次只读 SQL 预览使用的已启用 JDBC 数据源 UUID。")
        @NotNull UUID dataSourceId,
        @Schema(description = "有序关联模型 UUID 列表，至少一个且都必须使用 dataSourceId 作为存储数据源；模型状态不限。该列表用于来源说明、血缘、编辑辅助和删除保护，不限制 SQL 实际访问的表。")
        @NotEmpty List<@NotNull UUID> modelIds,
        @Schema(description = "只允许一条 SELECT 或 WITH ... SELECT 的参数化 SQL，最长 100000 字符。可使用已声明的命名参数；平台不解析或限制 SQL 实际访问的表。")
        @NotBlank @Size(max = 100_000) String sqlText,
        @Schema(description = "SQL 中允许引用的命名参数定义；名称必须唯一、全部被 SQL 使用，SQL 中出现的参数也必须全部声明，且不支持 BINARY 或 GEOMETRY 参数。")
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters,
        @Schema(description = "按参数名提供的试运行值；未知键会被拒绝，必填参数必须提供非空值，可选参数省略或传 null 时按 SQL NULL 绑定，其他值按对应平台类型校验。")
        Map<String, Object> arguments,
        @Schema(description = "最多返回的预览行数；省略时为 20，范围 1～50。")
        @Min(1) @Max(50) Integer previewSize
) {

    public SqlServiceTestRequest {
        modelIds = modelIds == null ? List.of() : List.copyOf(modelIds);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
