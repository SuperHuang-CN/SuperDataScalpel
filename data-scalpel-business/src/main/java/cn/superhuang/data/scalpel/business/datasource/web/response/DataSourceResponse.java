package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DatabaseType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        Set<DataSourcePurpose> purposes,
        DatabaseType databaseType,
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
                dataSource.getDatabaseType(),
                dataSource.isEnabled(),
                dataSource.getDescription(),
                DataSourceConnectionResponse.from(dataSource.getConnection()),
                dataSource.getCreatedAt(),
                dataSource.getUpdatedAt()
        );
    }
}
