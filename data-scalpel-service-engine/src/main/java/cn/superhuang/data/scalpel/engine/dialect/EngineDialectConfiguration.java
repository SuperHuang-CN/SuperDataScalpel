package cn.superhuang.data.scalpel.engine.dialect;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EngineDialectConfiguration {

    @Bean
    DialectRegistry dialectRegistry() {
        return BuiltInDialects.registry();
    }
}
