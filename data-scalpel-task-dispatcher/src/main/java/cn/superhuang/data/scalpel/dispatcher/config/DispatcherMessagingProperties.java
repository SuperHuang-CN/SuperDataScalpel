package cn.superhuang.data.scalpel.dispatcher.config;

import cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned channels. Legacy scope is only for controlled upgrades of old installations. */
@ConfigurationProperties("data-scalpel.dispatcher.messaging")
public record DispatcherMessagingProperties(Scope scope, String commandTopic, String runnerEventTopic,
                                           String adminEventTopic) {
    public enum Scope { INSTANCE, LEGACY_ENGINE }

    public DispatcherMessagingProperties {
        scope = scope == null ? Scope.LEGACY_ENGINE : scope;
        adminEventTopic = adminEventTopic == null ? "datascalpel.execution.event" : adminEventTopic;
        if (scope == Scope.INSTANCE) {
            for (String topic : new String[]{commandTopic, runnerEventTopic, adminEventTopic}) {
                if (topic == null || !topic.matches("[a-zA-Z0-9._-]{1,241}") || topic.equals(".") || topic.equals("..")) {
                    throw new IllegalArgumentException("实例消息通道必须配置合法且稳定的 Topic 名称（最长 241 字符）");
                }
            }
            if (java.util.Set.of(commandTopic, runnerEventTopic, adminEventTopic, runnerEventTopic + ".control").size() != 4) {
                throw new IllegalArgumentException("实例消息通道不能重名");
            }
        }
    }

    public boolean shared() { return scope == Scope.INSTANCE; }
    public DispatcherTopics topics() { return new DispatcherTopics(commandTopic, runnerEventTopic, adminEventTopic); }
    public DispatcherTargetDirectoryResponse.Messaging discovery() {
        return shared() ? new DispatcherTargetDirectoryResponse.Messaging(commandTopic, runnerEventTopic,
                adminEventTopic, topics().runnerControlTopic()) : null;
    }
}
