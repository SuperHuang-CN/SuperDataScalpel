package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "浏览器安全的 S3 对象存储连接配置；不返回 Secret Key。")
public record S3DataSourceConnectionResponse(
        @Schema(description = "S3 兼容服务 Endpoint；使用 AWS 默认端点时可为空") String endpoint,
        @Schema(description = "对象存储 Region；服务不要求时可为空") String region,
        @Schema(description = "该数据源固定绑定的 Bucket") String bucket,
        @Schema(description = "Bucket 内允许访问的根前缀；空表示从 Bucket 根目录开始") String rootPrefix,
        @Schema(description = "Access Key 标识") String accessKey,
        @Schema(description = "是否已保存 Secret Key；只表示存在，不泄露密钥值。") boolean secretKeyConfigured,
        @Schema(description = "是否使用路径风格地址；兼容 MinIO 等服务时通常为 true") boolean pathStyleAccess
) implements DataSourceConnectionResponse {
    public static S3DataSourceConnectionResponse from(DataSourceConnection connection) {
        return new S3DataSourceConnectionResponse(
                connection.getEndpoint(), connection.getOptions().get("region"), connection.getTarget(),
                connection.getNamespace(), connection.getPrincipal(), connection.hasPassword(),
                Boolean.parseBoolean(connection.getOptions().getOrDefault("pathStyleAccess", "true"))
        );
    }

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.S3;
    }
}
