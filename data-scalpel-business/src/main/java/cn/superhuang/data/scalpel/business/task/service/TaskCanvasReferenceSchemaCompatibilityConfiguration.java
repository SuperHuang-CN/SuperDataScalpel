package cn.superhuang.data.scalpel.business.task.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Evolves legacy single-output uniqueness constraints for Canvas nodes. Hibernate schema update
 * adds columns but does not reliably replace existing PostgreSQL unique constraints.
 */
@Configuration(proxyBeanMethods = false)
class TaskCanvasReferenceSchemaCompatibilityConfiguration {

    private static final String MODEL_REFERENCE_LEGACY_CONSTRAINT =
            "uk_task_canvas_model_reference_task_node";
    private static final String MODEL_REFERENCE_CONSTRAINT =
            "uk_task_canvas_model_reference_location";
    private static final String STREAMING_QUERY_LEGACY_CONSTRAINT =
            "uk_task_streaming_query_output";
    private static final String STREAMING_QUERY_CONSTRAINT =
            "uk_task_streaming_query_output_write";

    @Bean
    @Order(-90)
    ApplicationRunner synchronizeCanvasMultiResourceConstraints(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return arguments -> {
            if (!isPostgreSql(dataSource)) return;
            replaceUniqueConstraint(
                    jdbcTemplate,
                    "task_canvas_model_reference",
                    MODEL_REFERENCE_LEGACY_CONSTRAINT,
                    MODEL_REFERENCE_CONSTRAINT,
                    "task_id, node_id, model_id, reference_role"
            );
            replaceUniqueConstraint(
                    jdbcTemplate,
                    "task_streaming_query",
                    STREAMING_QUERY_LEGACY_CONSTRAINT,
                    STREAMING_QUERY_CONSTRAINT,
                    "deployment_id, output_node_id, output_write_id"
            );
        };
    }

    private static void replaceUniqueConstraint(
            JdbcTemplate jdbcTemplate,
            String table,
            String legacyConstraint,
            String currentConstraint,
            String columns
    ) {
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + legacyConstraint);
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + currentConstraint);
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + currentConstraint
                + " UNIQUE (" + columns + ")");
    }

    private static boolean isPostgreSql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取管理数据库类型", exception);
        }
    }
}
