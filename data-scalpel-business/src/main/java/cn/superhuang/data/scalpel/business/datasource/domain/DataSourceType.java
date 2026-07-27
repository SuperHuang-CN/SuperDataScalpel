package cn.superhuang.data.scalpel.business.datasource.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * A concrete reusable data-source product. JDBC products and non-JDBC connectors share the same
 * data-source aggregate while retaining their own connection configuration shape.
 */
public enum DataSourceType {
    MYSQL(DataSourceConnectionKind.JDBC, "MySQL", allPurposes()),
    POSTGRESQL(DataSourceConnectionKind.JDBC, "PostgreSQL", allPurposes()),
    ORACLE(DataSourceConnectionKind.JDBC, "Oracle", allPurposes()),
    SQL_SERVER(DataSourceConnectionKind.JDBC, "SQL Server", allPurposes()),
    CLICKHOUSE(DataSourceConnectionKind.JDBC, "ClickHouse", allPurposes()),
    DAMENG(DataSourceConnectionKind.JDBC, "达梦", allPurposes()),
    KINGBASE(DataSourceConnectionKind.JDBC, "人大金仓", allPurposes()),
    OPENGAUSS(DataSourceConnectionKind.JDBC, "openGauss", allPurposes()),
    KAFKA(DataSourceConnectionKind.KAFKA, "Kafka", EnumSet.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION)),
    S3(DataSourceConnectionKind.S3, "S3 兼容对象存储", EnumSet.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION)),
    HTTP_API(DataSourceConnectionKind.HTTP_API, "HTTP API", EnumSet.of(DataSourcePurpose.SOURCE));

    private final DataSourceConnectionKind connectionKind;
    private final String displayName;
    private final Set<DataSourcePurpose> supportedPurposes;

    DataSourceType(
            DataSourceConnectionKind connectionKind,
            String displayName,
            Set<DataSourcePurpose> supportedPurposes
    ) {
        this.connectionKind = connectionKind;
        this.displayName = displayName;
        this.supportedPurposes = Set.copyOf(supportedPurposes);
    }

    public DataSourceConnectionKind connectionKind() {
        return connectionKind;
    }

    public String displayName() {
        return displayName;
    }

    public Set<DataSourcePurpose> supportedPurposes() {
        return supportedPurposes;
    }

    public boolean isJdbc() {
        return connectionKind == DataSourceConnectionKind.JDBC;
    }

    private static Set<DataSourcePurpose> allPurposes() {
        return EnumSet.allOf(DataSourcePurpose.class);
    }
}
