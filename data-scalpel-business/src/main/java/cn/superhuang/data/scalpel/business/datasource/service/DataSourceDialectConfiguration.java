package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataSourceDialectConfiguration {

    @Bean
    DialectRegistry dialectRegistry() {
        return BuiltInDialects.registry();
    }

    @Bean
    DatabaseInspector databaseInspector(DialectRegistry registry) {
        return new DatabaseInspector(registry, new JdbcConnectionFactory());
    }
}
