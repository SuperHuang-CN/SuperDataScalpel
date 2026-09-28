package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;

/** Resolve a target's explicit values over the built-in, externally overridable backend defaults. */
public final class DispatcherTargetConfiguration {
    private DispatcherTargetConfiguration() { }

    public static DispatcherTargetsProperties.Target resolve(Environment environment, String key,
                                                              DispatcherTargetsProperties.Target target) {
        return new DispatcherTargetsProperties.Target(target.enabled(), target.name(), target.backend(),
                target.backend() == cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER
                        ? backend(environment, key, "local-docker", LocalDockerProperties.class) : null,
                target.backend() == cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.YARN
                        ? backend(environment, key, "yarn", YarnProperties.class) : null,
                target.backend() == cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.KUBERNETES
                        ? backend(environment, key, "kubernetes", KubernetesProperties.class) : null,
                target.streaming(), target.resourcePolicy());
    }

    public static <T> T backend(Environment environment, String key, String section, Class<T> type) {
        var binder = Binder.get(environment);
        var values = new LinkedHashMap<String, String>();
        values.putAll(binder.bind("data-scalpel.dispatcher.backend-defaults." + section,
                Bindable.mapOf(String.class, String.class)).orElseGet(java.util.Map::of));
        values.putAll(binder.bind("data-scalpel.dispatcher.targets." + key + "." + section,
                Bindable.mapOf(String.class, String.class)).orElseGet(java.util.Map::of));
        var prefixed = new LinkedHashMap<String, Object>();
        values.forEach((name, value) -> prefixed.put("backend." + name, value));
        return new Binder(new MapConfigurationPropertySource(prefixed)).bindOrCreate("backend", type);
    }
}
