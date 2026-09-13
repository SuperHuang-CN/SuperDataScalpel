package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

@Schema(description = "整体修改数据源基本信息与连接；code 不可修改。保持相同产品类型时，敏感连接字段传 null 可保留当前值；切换产品类型时必须提供新类型所需凭据")
public record UpdateDataSourceRequest(
        @Schema(description = "数据源显示名称")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属 DATA_SOURCE 范围目录 UUID；null 表示移出目录。")
        UUID directoryId,
        @Schema(description = "数据源承担的业务用途；必须属于所选 type 的 supportedPurposes")
        @NotEmpty Set<@NotNull DataSourcePurpose> purposes,
        @Schema(description = "具体数据源产品类型；必须与 connection.kind 一致")
        @NotNull DataSourceType type,
        @Schema(description = "人工启停状态；false 会阻止任务等运行能力继续使用，但仍可查看配置和执行部分管理探测。已被 GeoServer 或已启用数据服务使用时可能不允许停用")
        @NotNull Boolean enabled,
        @Schema(description = "数据源用途、负责人或使用限制等说明；null 表示清空")
        @Size(max = 1000) String description,
        @Schema(description = "完整连接配置；保持相同产品类型和相同认证方式时，密码、密钥等敏感字段传 null 保留已保存值。切换产品类型或认证方式时必须提供新配置所需凭据")
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
