package cn.superhuang.data.scalpel.business.system.configuration.service;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class SystemConfigurationInitializer {

    @Bean
    @Order(0)
    ApplicationRunner initializeSystemConfigurations(
            SystemConfigurationRepository repository,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return arguments -> transactionTemplate.executeWithoutResult(status -> {
            for (SystemConfigurationDefinition definition : SystemConfigurationDefinition.values()) {
                if (repository.findByConfigKey(definition.getConfigKey()).isEmpty()) {
                    repository.save(definition.newEntity());
                }
            }
        });
    }
}
