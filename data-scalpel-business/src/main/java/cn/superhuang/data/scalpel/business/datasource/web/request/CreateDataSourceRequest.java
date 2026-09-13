package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

@Schema(description = "创建一个可复用的数据源连接；连接结构必须与 type 对应，code 创建后不可修改")
public record CreateDataSourceRequest(
        @Schema(description = "数据源全局唯一技术编码；以字母开头，只能包含字母、数字和下划线", example = "ods_postgresql")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "数据源显示名称", example = "业务库 PostgreSQL")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属 DATA_SOURCE 范围目录 UUID；null 表示不归入目录。")
        UUID directoryId,
        @Schema(description = "数据源承担的业务用途；必须属于所选 type 的 supportedPurposes")
        @NotEmpty Set<@NotNull DataSourcePurpose> purposes,
        @Schema(description = "具体数据源产品类型，决定 connection 的结构与可用能力")
        @NotNull DataSourceType type,
        @Schema(description = "人工启停状态；省略时默认为 true，不表示实时连接健康度")
        Boolean enabled,
        @Schema(description = "数据源用途、负责人或使用限制等说明")
        @Size(max = 1000) String description,
        @Schema(description = "与 type.connectionKind 匹配的连接配置；通过 kind 区分 JDBC、Kafka、S3 或 HTTP_API")
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
