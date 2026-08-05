package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Migrates the old PUBLISHED value, whose real meaning was "deployed to Engine", to ENABLED.
 * Hibernate schema update does not evolve PostgreSQL enum check constraints by itself.
 */
@Configuration(proxyBeanMethods = false)
class DataServiceStatusCompatibilityConfiguration {

    private static final String STATUS_CHECK_CONSTRAINT = "ds_data_service_status_check";
    private static final String TYPE_CHECK_CONSTRAINT = "ds_data_service_type_check";

    @Bean
    @Order(-90)
    ApplicationRunner migrateDataServiceStatus(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return arguments -> {
            if (!isPostgreSql(dataSource)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE ds_data_service DROP CONSTRAINT IF EXISTS " + STATUS_CHECK_CONSTRAINT);
            jdbcTemplate.update("UPDATE ds_data_service SET status = 'ENABLED' WHERE status = 'PUBLISHED'");
            jdbcTemplate.update("UPDATE ds_data_service SET access_mode = 'PUBLIC' WHERE access_mode IS NULL");
            jdbcTemplate.execute("ALTER TABLE ds_data_service ADD CONSTRAINT " + STATUS_CHECK_CONSTRAINT
                    + " CHECK (status IN (" + supportedStatuses() + "))");
            jdbcTemplate.execute("ALTER TABLE ds_data_service DROP CONSTRAINT IF EXISTS " + TYPE_CHECK_CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE ds_data_service ADD CONSTRAINT " + TYPE_CHECK_CONSTRAINT
                    + " CHECK (type IN (" + supportedTypes() + "))");
        };
    }

    private static boolean isPostgreSql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取管理数据库类型", exception);
        }
    }

    private static String supportedStatuses() {
        return Arrays.stream(DataServiceStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
    }

    private static String supportedTypes() {
        return Arrays.stream(DataServiceType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
