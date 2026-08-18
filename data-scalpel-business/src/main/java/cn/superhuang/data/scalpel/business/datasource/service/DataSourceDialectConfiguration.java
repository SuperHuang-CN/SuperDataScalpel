package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseStandardQueryExecutor;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseSpatialPreviewExecutor;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseTableOperator;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcInsertSelectExecutor;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataSourceDialectConfiguration {

    @Bean
    DialectRegistry dialectRegistry() {
        return BuiltInDialects.registry();
    }

    @Bean
    JdbcConnectionFactory jdbcConnectionFactory() {
        return new JdbcConnectionFactory();
    }

    @Bean
    DatabaseInspector databaseInspector(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        return new DatabaseInspector(registry, connectionFactory);
    }

    @Bean
    DatabaseStandardQueryExecutor databaseStandardQueryExecutor(
            DialectRegistry registry,
            JdbcConnectionFactory connectionFactory
    ) {
        return new DatabaseStandardQueryExecutor(registry, connectionFactory);
    }

    @Bean
    DatabaseSpatialPreviewExecutor databaseSpatialPreviewExecutor(
            DialectRegistry registry,
            JdbcConnectionFactory connectionFactory
    ) {
        return new DatabaseSpatialPreviewExecutor(registry, connectionFactory);
    }

    @Bean
    DatabaseTableOperator databaseTableOperator(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        return new DatabaseTableOperator(registry, connectionFactory);
    }

    @Bean
    JdbcQueryInspector jdbcQueryInspector(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        return new JdbcQueryInspector(registry, connectionFactory);
    }

    @Bean
    JdbcInsertSelectExecutor jdbcInsertSelectExecutor(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        return new JdbcInsertSelectExecutor(registry, connectionFactory);
    }
}
