package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Keeps the PostgreSQL enum check created by ddl-auto aligned when operation types grow. */
@Configuration(proxyBeanMethods = false)
class DataEntrySchemaCompatibilityConfiguration {

    private static final String OPERATION_TYPE_CHECK = "ds_data_entry_operation_log_operation_type_check";

    @Bean
    @Order(-95)
    ApplicationRunner synchronizeDataEntryOperationTypeConstraint(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return arguments -> {
            if (!isPostgreSql(dataSource)) return;
            jdbcTemplate.execute("ALTER TABLE ds_data_entry_operation_log DROP CONSTRAINT IF EXISTS "
                    + OPERATION_TYPE_CHECK);
            jdbcTemplate.execute("ALTER TABLE ds_data_entry_operation_log ADD CONSTRAINT " + OPERATION_TYPE_CHECK
                    + " CHECK (operation_type IN (" + supportedTypes() + "))");
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
        return Arrays.stream(DataEntryOperationType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
