package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DispatcherStartupValidator implements ApplicationRunner {

    private final DispatcherProperties properties;

    public DispatcherStartupValidator(DispatcherProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new IllegalStateException("DATASCALPEL_TASK_DISPATCHER_TOKEN 不能为空");
        }
    }
}
