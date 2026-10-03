package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
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

/** Hibernate update does not extend an existing enum check constraint. */
@Configuration(proxyBeanMethods = false)
class FileDatasetSchemaCompatibilityConfiguration {
    @Bean
    @Order(-95)
    ApplicationRunner synchronizeFileDatasetParseStatus(DataSource dataSource, JdbcTemplate jdbc,
                                                       PlatformTransactionManager manager) {
        return arguments -> {
            try (var connection = dataSource.getConnection()) {
                if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
            }
            String states = Arrays.stream(FileDatasetParseStatus.values())
                    .map(state -> "'" + state.name() + "'").collect(Collectors.joining(","));
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.execute("ALTER TABLE ds_file_dataset_table DROP CONSTRAINT IF EXISTS ds_file_dataset_table_parse_status_check");
                jdbc.execute("ALTER TABLE ds_file_dataset_table ADD CONSTRAINT ds_file_dataset_table_parse_status_check CHECK (parse_status IN (" + states + "))");
            });
        };
    }
}
