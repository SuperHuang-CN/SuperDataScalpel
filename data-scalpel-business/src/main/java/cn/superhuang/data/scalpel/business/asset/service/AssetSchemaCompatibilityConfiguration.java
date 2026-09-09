package cn.superhuang.data.scalpel.business.asset.service;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
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

/** ddl-auto=update does not extend an existing PostgreSQL enum check constraint. */
@Configuration(proxyBeanMethods = false)
class AssetSchemaCompatibilityConfiguration {
    @Bean
    @Order(-94)
    ApplicationRunner synchronizeAssetTypeConstraint(DataSource dataSource, JdbcTemplate jdbc,
                                                     PlatformTransactionManager transactions) {
        return arguments -> {
            try (var connection = dataSource.getConnection()) {
                if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
            }
            String values = Arrays.stream(AssetType.values()).map(type -> "'" + type.name() + "'").collect(Collectors.joining(", "));
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.execute("ALTER TABLE ds_asset DROP CONSTRAINT IF EXISTS ds_asset_asset_type_check");
                jdbc.execute("ALTER TABLE ds_asset ADD CONSTRAINT ds_asset_asset_type_check CHECK (asset_type IN (" + values + "))");
            });
        };
    }
}
