package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        Set<DataSourcePurpose> purposes,
        DataSourceType type,
        DataSourceConnectionKind connectionKind,
        boolean enabled,
        String description,
        DataSourceConnectionResponse connection,
        Instant createdAt,
        Instant updatedAt
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
        };
    }
}
