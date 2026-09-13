package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "已登记的数据源及浏览器安全连接配置")
public record DataSourceResponse(
        @Schema(description = "数据源 UUID") UUID id,
        @Schema(description = "数据源全局唯一技术编码，创建后不可修改") String code,
        @Schema(description = "数据源显示名称") String name,
        @Schema(description = "所属 DATA_SOURCE 范围目录 UUID；未归入目录时为空。") UUID directoryId,
        @Schema(description = "数据源承担的业务用途集合，决定能否用于输入读取、结果存储、消息流或分发等场景") Set<DataSourcePurpose> purposes,
        @Schema(description = "具体数据源产品类型，决定驱动、连接参数和资源浏览能力") DataSourceType type,
        @Schema(description = "连接结构类型：JDBC、KAFKA、S3 或 HTTP_API；与 connection 的多态结构一致") DataSourceConnectionKind connectionKind,
        @Schema(description = "人工启停状态；不表示当前实时连接健康度") boolean enabled,
        @Schema(description = "数据源用途、范围或运维备注") String description,
        @Schema(description = "浏览器安全的连接配置；不包含密码、Token 或密钥。") DataSourceConnectionResponse connection,
        @Schema(description = "数据源创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "数据源最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static DataSourceResponse from(DataSource dataSource) {
        return new DataSourceResponse(
                dataSource.getId(),
                dataSource.getCode(),
                dataSource.getName(),
                dataSource.getDirectoryId(),
                dataSource.getPurposes(),
                dataSource.getType(),
                dataSource.getType().connectionKind(),
                dataSource.isEnabled(),
                dataSource.getDescription(),
                connectionResponse(dataSource),
                dataSource.getCreatedAt(),
                dataSource.getUpdatedAt()
        );
    }

    private static DataSourceConnectionResponse connectionResponse(DataSource dataSource) {
        return switch (dataSource.getType().connectionKind()) {
            case JDBC -> JdbcDataSourceConnectionResponse.from(dataSource.getConnection());
            case KAFKA -> KafkaDataSourceConnectionResponse.from(dataSource.getConnection());
            case S3 -> S3DataSourceConnectionResponse.from(dataSource.getConnection());
            case HTTP_API -> HttpApiDataSourceConnectionResponse.from(dataSource.getConnection());
        };
    }
}
