package cn.superhuang.data.scalpel.business.system.configuration.service;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration(proxyBeanMethods = false)
class SystemConfigurationInitializer {

    @Bean
    ApplicationRunner initializeSystemConfigurations(SystemConfigurationRepository repository) {
        return arguments -> insertMissingConfigurations(repository);
    }

    @Transactional
    void insertMissingConfigurations(SystemConfigurationRepository repository) {
        for (SystemConfigurationDefinition definition : SystemConfigurationDefinition.values()) {
            if (repository.findByConfigKey(definition.getConfigKey()).isEmpty()) {
                repository.save(definition.newEntity());
            }
        }
    }
}
