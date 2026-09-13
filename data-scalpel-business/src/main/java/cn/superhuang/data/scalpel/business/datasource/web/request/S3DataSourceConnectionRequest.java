package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One S3 data source is explicitly scoped to one bucket and an optional root prefix. */
@Schema(description = "S3 兼容对象存储连接；一个数据源固定绑定一个 Bucket 和可选根前缀")
public record S3DataSourceConnectionRequest(
        @Schema(description = "S3 服务端点，包含 http/https 协议和可选端口", example = "https://s3.example.internal")
        @NotBlank @Size(max = 255) String endpoint,
        @Schema(description = "对象存储 Region；服务不要求 Region 时可空", example = "us-east-1")
        @Size(max = 64) String region,
        @Schema(description = "固定访问的 Bucket 名称")
        @NotBlank @Size(max = 63) String bucket,
        @Schema(description = "Bucket 内允许访问的根 Key 前缀；空值表示 Bucket 根目录，不提供对象浏览")
        @Size(max = 512) String rootPrefix,
        @Schema(description = "S3 Access Key")
        @NotBlank @Size(max = 128) String accessKey,
        @Schema(description = "S3 Secret Key，只写不返回；保持 S3 类型更新时传 null 保留原密钥，创建或从其他类型切换到 S3 时必须提供", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 512) String secretKey,
        @Schema(description = "是否使用 Path-style 地址；兼容 MinIO 等服务时通常为 true，省略时默认为 true。")
        Boolean pathStyleAccess
) implements DataSourceConnectionRequest {
    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.S3;
    }
}
