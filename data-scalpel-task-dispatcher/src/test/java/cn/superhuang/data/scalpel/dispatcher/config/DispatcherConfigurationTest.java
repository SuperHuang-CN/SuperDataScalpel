package cn.superhuang.data.scalpel.dispatcher.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class DispatcherConfigurationTest {
    @Test void dockerPolicyNeedsOnlyDriverWhileClusterRequiresExecutors() {
        var values = new DispatcherTargetsProperties.Resources(2, 4096, null, null, null);
        var policy = new DispatcherTargetsProperties.ResourcePolicy(values, values);
        var resolved = policy.resolve(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER);
        assertThat(resolved.defaults().driverMemoryMiB()).isEqualTo(4096);
        assertThat(resolved.defaults().executorInstances()).isEqualTo(1);
        for (var backend : java.util.List.of(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.YARN,
                cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.KUBERNETES)) {
            assertThatThrownBy(() -> policy.resolve(backend)).hasMessageContaining("必须配置 Executor");
        }
    }

    @Test void executorValuesCannotRejectDockerButRemainLimitedForClusters() {
        var resources = new cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec(2, 4096, 50, 16, 65536);
        var maximums = new cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec(4, 8192, 2, 2, 2048);
        assertThat(resources.exceeds(maximums, cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER)).isFalse();
        assertThat(resources.exceeds(maximums, cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.YARN)).isTrue();
        assertThat(resources.exceeds(maximums, cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.KUBERNETES)).isTrue();
    }
    @Test void targetResourcePoliciesBindAndCanBeOverriddenIndependently() throws Exception {
        var env = packaged();
        var prefix = "data-scalpel.dispatcher.targets.k8s.resource-policy.";
        var overrides = new java.util.HashMap<String, Object>();
        overrides.put(prefix + "defaults.driver-cores", "3");
        overrides.put(prefix + "defaults.driver-memory-mi-b", "3072");
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("overrides", overrides));
        var properties = Binder.get(env).bind("data-scalpel.dispatcher", DispatcherTargetsProperties.class).get();
        var target = properties.targets().get("k8s");
        assertThat(target.resourcePolicy().defaults().driverCores()).isEqualTo(3);
        assertThat(target.resourcePolicy().defaults().driverMemoryMiB()).isEqualTo(3072);
        assertThat(target.resourcePolicy().maximums().driverMemoryMiB()).isEqualTo(16384);
        assertThat(properties.targets().get("local-docker").resourcePolicy().defaults().driverCores()).isEqualTo(2);
        assertThat(DispatcherTargetConfiguration.resolve(env, "k8s", target).resourcePolicy()).isEqualTo(target.resourcePolicy());
        overrides.put(prefix + "defaults.driver-cores", "9");
        assertThatThrownBy(() -> Binder.get(env).bind("data-scalpel.dispatcher", DispatcherTargetsProperties.class))
                .hasRootCauseMessage("Spark 运行资源默认值不能超过上限");
    }
    @Test void springLoadsInstanceProfileAndExternalOverrides(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("application-instance.yml"), """
                server.port: 19192
                data-scalpel.dispatcher:
                  observation-poll-interval: 17s
                  messaging.command-topic: commands.external
                  messaging.runner-event-topic: runners.external
                """);
        var env = new MockEnvironment();
        // Test-only directory; production uses Spring's default ./config/ search location.
        env.setProperty("spring.config.additional-location", directory.toUri().toString());
        org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.applyTo(env);
        assertThat(env.getActiveProfiles()).contains("instance");
        assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(19192);
        assertThat(env.getProperty("data-scalpel.dispatcher.observation-poll-interval")).isEqualTo("17s");
        assertThat(env.getProperty("data-scalpel.dispatcher.outbox-claim-timeout")).isEqualTo("1m");
        assertThat(Binder.get(env).bind("data-scalpel.dispatcher.messaging", DispatcherMessagingProperties.class).get().commandTopic())
                .isEqualTo("commands.external");
    }

    @Test void retainsDefaultsAndAllThreeExplicitlyDisabledExamples() throws Exception {
        var env = packaged();
        env.setProperty("DATASCALPEL_TASK_DISPATCHER_BACKEND", "LOCAL_DOCKER");
        var properties = Binder.get(env).bind("data-scalpel.dispatcher", DispatcherTargetsProperties.class).get();
        assertThat(properties.targets()).containsKeys("local-docker", "k8s", "yarn");
        assertThat(properties.targets().values()).allMatch(target -> !target.enabled());
        assertThat(properties.targets().get("local-docker").backend().name()).isEqualTo("LOCAL_DOCKER");
        assertThat(properties.targets().get("k8s").backend().name()).isEqualTo("KUBERNETES");
        assertThat(properties.targets().get("yarn").backend().name()).isEqualTo("YARN");
        assertThat(env.getProperty("spring.profiles.include")).isEqualTo("instance");
        assertThat(Binder.get(env).bind("data-scalpel.dispatcher.messaging", DispatcherMessagingProperties.class).get().shared()).isTrue();
    }

    @Test void externalOverridesKeepDefaultsAndTargetOverridesAreIsolated() throws Exception {
        var env = packaged();
        String yaml = """
                data-scalpel:
                  dispatcher:
                    observation-poll-interval: 13s
                    backend-defaults:
                      kubernetes:
                        command-timeout: 41s
                    targets:
                      k8s-a:
                        enabled: true
                        backend: KUBERNETES
                        kubernetes:
                          master: k8s://https://cluster-a:6443
                          namespace: test-a
                          command-timeout: 7s
                      k8s-b:
                        enabled: true
                        backend: KUBERNETES
                        kubernetes:
                          master: k8s://https://cluster-b:6443
                          namespace: test-b
                """;
        new YamlPropertySourceLoader().load("external", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                .forEach(source -> env.getPropertySources().addFirst(source));
        assertThat(Binder.get(env).bind("data-scalpel.dispatcher", DispatcherProperties.class).get().observationPollInterval())
                .isEqualTo(Duration.ofSeconds(13));
        var a = DispatcherTargetConfiguration.backend(env, "k8s-a", "kubernetes", KubernetesProperties.class);
        var b = DispatcherTargetConfiguration.backend(env, "k8s-b", "kubernetes", KubernetesProperties.class);
        assertThat(a.commandTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(b.commandTimeout()).isEqualTo(Duration.ofSeconds(41));
        assertThat(a.namespace()).isEqualTo("test-a");
        assertThat(b.namespace()).isEqualTo("test-b");
        assertThat(a.cancelGraceSeconds()).isEqualTo(10);
        assertThat(a.driverMemory()).isEqualTo("2g");
        assertThat(Binder.get(env).bind("data-scalpel.dispatcher", DispatcherTargetsProperties.class).get()
                .targets().get("local-docker").enabled()).isFalse();
    }

    @Test void validatesSharedTopicsAndDefaultsAdminChannel() {
        var config = new DispatcherMessagingProperties(DispatcherMessagingProperties.Scope.INSTANCE, "commands.a", "runners.a", null);
        assertThat(config.adminEventTopic()).isEqualTo("datascalpel.execution.event");
        assertThat(config.topics().runnerControlTopic()).isEqualTo("runners.a.control");
        assertThatThrownBy(() -> new DispatcherMessagingProperties(DispatcherMessagingProperties.Scope.INSTANCE, "same", "same", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DispatcherMessagingProperties(DispatcherMessagingProperties.Scope.INSTANCE, null, "valid", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void retainsEnvironmentOverridesAfterMovingDefaults() throws Exception {
        var env = packaged();
        env.setProperty("DATASCALPEL_ADMIN_EVENT_TOPIC", "events.custom");
        env.setProperty("DATASCALPEL_FILE_STORAGE_BUCKET", "custom-bucket");
        env.setProperty("DATASCALPEL_KAFKA_RUNNER_SECURITY_PROTOCOL", "SSL");
        assertThat(env.getProperty("data-scalpel.dispatcher.messaging.admin-event-topic")).isEqualTo("events.custom");
        assertThat(env.getProperty("data-scalpel.dispatcher.artifact.bucket")).isEqualTo("custom-bucket");
        assertThat(env.getProperty("data-scalpel.dispatcher.runner-kafka.security-protocol")).isEqualTo("SSL");
    }

    private MockEnvironment packaged() throws Exception {
        var environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        loader.load("defaults", new ClassPathResource("application.yml")).forEach(source -> environment.getPropertySources().addLast(source));
        loader.load("instance", new ClassPathResource("application-instance.yml")).forEach(source -> environment.getPropertySources().addFirst(source));
        return environment;
    }
}
