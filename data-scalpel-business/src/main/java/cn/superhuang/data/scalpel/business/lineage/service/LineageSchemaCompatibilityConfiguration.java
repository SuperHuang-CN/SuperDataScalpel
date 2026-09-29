package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Aligns the existing enum constraint; Hibernate update does not extend PostgreSQL checks. */
@Configuration(proxyBeanMethods = false)
class LineageSchemaCompatibilityConfiguration {
    @Bean
    @Order(-93)
    ApplicationRunner synchronizeLineageWriteModeConstraint(DataSource dataSource, JdbcTemplate jdbc,
                                                            PlatformTransactionManager transactions) {
        return arguments -> {
            try (var connection = dataSource.getConnection()) {
                if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
            }
            String values = Arrays.stream(LineageWriteMode.values()).map(mode -> "'" + mode.name() + "'")
                    .collect(Collectors.joining(", "));
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.execute("ALTER TABLE task_lineage_asset DROP CONSTRAINT IF EXISTS task_lineage_asset_write_mode_check");
                jdbc.execute("ALTER TABLE task_lineage_asset ADD CONSTRAINT task_lineage_asset_write_mode_check CHECK (write_mode IN (" + values + "))");
            });
        };
    }
}
