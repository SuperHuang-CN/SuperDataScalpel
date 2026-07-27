package cn.superhuang.data.scalpel.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.quartz.job-store-type", havingValue = "jdbc")
public class TaskQuartzConfiguration {

    @Bean
    SchedulerFactoryBeanCustomizer taskQuartzCustomizer() {
        return factory -> {
            Properties properties = new Properties();
            properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
            properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
            properties.setProperty("org.quartz.jobStore.isClustered", "true");
            properties.setProperty("org.quartz.jobStore.tablePrefix", "task_qrtz_");
            properties.setProperty("org.quartz.threadPool.threadCount", "4");
            factory.setQuartzProperties(properties);
        };
    }
}
