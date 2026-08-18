package cn.superhuang.data.scalpel.business.directory.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
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
 * Keeps the PostgreSQL directory-scope check constraint aligned with the Java enum.
 * Hibernate schema update does not evolve an existing enum check constraint.
 */
@Configuration(proxyBeanMethods = false)
class DirectorySchemaCompatibilityConfiguration {

    private static final String SCOPE_CHECK_CONSTRAINT = "ds_directory_scope_check";

    @Bean
    @Order(-95)
    ApplicationRunner synchronizeDirectoryScopeConstraint(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return arguments -> {
            if (!isPostgreSql(dataSource)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE ds_directory DROP CONSTRAINT IF EXISTS " + SCOPE_CHECK_CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE ds_directory ADD CONSTRAINT " + SCOPE_CHECK_CONSTRAINT
                    + " CHECK (scope IN (" + supportedScopes() + "))");
        };
    }

    private static boolean isPostgreSql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取管理数据库类型", exception);
        }
    }

    private static String supportedScopes() {
        return Arrays.stream(DirectoryScope.values())
                .map(scope -> "'" + scope.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
