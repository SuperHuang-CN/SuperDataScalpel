package cn.superhuang.data.scalpel.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** Runtime-only bounded executor for the first local SQL task implementation. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalSqlTaskExecutionConfiguration.Properties.class)
public class LocalSqlTaskExecutionConfiguration {

    @Bean("localSqlTaskExecutor")
    ThreadPoolTaskExecutor localSqlTaskExecutor(Properties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getConcurrency());
        executor.setMaxPoolSize(properties.getConcurrency());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("local-sql-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @ConfigurationProperties(prefix = "data-scalpel.task-run.executor")
    public static class Properties {

        private int concurrency = 4;
        private int queueCapacity = 100;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            if (concurrency < 1 || concurrency > 32) {
                throw new IllegalArgumentException("任务执行并发数必须在 1 到 32 之间");
            }
            this.concurrency = concurrency;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            if (queueCapacity < 1 || queueCapacity > 1000) {
                throw new IllegalArgumentException("任务执行队列容量必须在 1 到 1000 之间");
            }
            this.queueCapacity = queueCapacity;
        }
    }
}
