package cn.superhuang.data.scalpel.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "data-scalpel.gateway-access",
        name = "enabled",
        havingValue = "true"
)
public class GatewayAccessKafkaConfiguration {

    @Bean("gatewayAccessKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> gatewayAccessKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(5_000L, FixedBackOff.UNLIMITED_ATTEMPTS)
        ));
        return factory;
    }
}
