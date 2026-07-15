package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
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
 * Repairs the enum check constraint created by Hibernate for installations that existed before
 * non-JDBC data-source types were introduced. Hibernate schema update does not evolve this
 * PostgreSQL constraint by itself.
 */
@Configuration(proxyBeanMethods = false)
class DataSourceSchemaCompatibilityConfiguration {

    private static final String TYPE_CHECK_CONSTRAINT = "ds_data_source_database_type_check";

    @Bean
    @Order(-100)
    ApplicationRunner synchronizeDataSourceTypeConstraint(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return arguments -> {
            if (!isPostgreSql(dataSource)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE ds_data_source DROP CONSTRAINT IF EXISTS " + TYPE_CHECK_CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE ds_data_source ADD CONSTRAINT " + TYPE_CHECK_CONSTRAINT
                    + " CHECK (database_type IN (" + supportedTypes() + "))");
        };
    }

    private static boolean isPostgreSql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取管理数据库类型", exception);
        }
    }

    private static String supportedTypes() {
        return Arrays.stream(DataSourceType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
