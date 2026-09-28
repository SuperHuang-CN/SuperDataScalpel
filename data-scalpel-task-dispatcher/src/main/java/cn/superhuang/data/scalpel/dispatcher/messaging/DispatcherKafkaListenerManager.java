package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherListenerManager;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandRecordReceiver;
import cn.superhuang.data.scalpel.dispatcher.messaging.runner.DispatcherRunnerRecordReceiver;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DispatcherKafkaListenerManager implements DispatcherListenerManager, ApplicationRunner {
    private final ConcurrentKafkaListenerContainerFactory<String, String> factory;
    private final DispatcherCommandRecordReceiver commandReceiver;
    private final DispatcherRunnerRecordReceiver runnerReceiver;
    private final cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository registrationRepository;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean ensureTopics;
    private final cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging;
    private final cn.superhuang.data.scalpel.dispatcher.management.DispatcherIdentityService identity;
    private final java.util.Map<java.util.UUID, ConcurrentMessageListenerContainer<String, String>> commands = new java.util.LinkedHashMap<>();
    private final java.util.Map<java.util.UUID, ConcurrentMessageListenerContainer<String, String>> runners = new java.util.LinkedHashMap<>();

    public DispatcherKafkaListenerManager(
            ConcurrentKafkaListenerContainerFactory<String, String> factory,
            DispatcherCommandRecordReceiver commandReceiver,
            DispatcherRunnerRecordReceiver runnerReceiver,
            cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository registrationRepository,
            KafkaAdmin kafkaAdmin,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${data-scalpel.dispatcher.ensure-topics:false}") boolean ensureTopics,
            cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging,
            cn.superhuang.data.scalpel.dispatcher.management.DispatcherIdentityService identity
    ) {
        this.factory = factory;
        this.commandReceiver = commandReceiver;
        this.runnerReceiver = runnerReceiver;
        this.registrationRepository = registrationRepository;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
        this.ensureTopics = ensureTopics;
        this.messaging = messaging;
        this.identity = identity;
    }

    @Override
    public synchronized void start(DispatcherRegistration registration) {
        if (messaging.shared()) {
            startShared();
            return;
        }
        stop(registration.getEngineId());
        String suffix = registration.getConsumerGroupSuffix();
        var commandContainer = factory.createContainer(registration.getCommandTopic());
        commandContainer.getContainerProperties().setGroupId("datascalpel-dispatcher-command-" + suffix);
        commandContainer.setupMessageListener((MessageListener<String, String>) record -> commandReceiver.receive(record, registration.getEngineId()));
        var runnerContainer = factory.createContainer(registration.getRunnerEventTopic());
        runnerContainer.getContainerProperties().setGroupId("datascalpel-dispatcher-runner-" + suffix);
        runnerContainer.setupMessageListener((MessageListener<String, String>) record -> runnerReceiver.receive(record, registration.getEngineId()));
        commands.put(registration.getEngineId(), commandContainer);
        runners.put(registration.getEngineId(), runnerContainer);
        commandContainer.start();
        runnerContainer.start();
    }

    @Override
    public synchronized void stopCommandListener() {
        commands.values().forEach(ConcurrentMessageListenerContainer::stop);
        commands.clear();
    }

    @Override
    @jakarta.annotation.PreDestroy
    public synchronized void stopAll() {
        commands.values().forEach(ConcurrentMessageListenerContainer::stop);
        runners.values().forEach(ConcurrentMessageListenerContainer::stop);
        commands.clear();
        runners.clear();
    }

    @Override
    public synchronized boolean listenersRunning() {
        return !commands.isEmpty() && commands.keySet().stream().allMatch(this::listenersRunning);
    }

    @Override
    public synchronized void stop(java.util.UUID engineId) {
        // An engine lifecycle transition must never stop the instance-wide consumer.
        if (messaging.shared()) return;
        var command = commands.remove(engineId);
        var runner = runners.remove(engineId);
        if (command != null) command.stop();
        if (runner != null) runner.stop();
    }

    @Override
    public synchronized boolean listenersRunning(java.util.UUID engineId) {
        if (messaging.shared()) engineId = identity.instanceId();
        var command = commands.get(engineId);
        var runner = runners.get(engineId);
        return command != null && command.isRunning() && runner != null && runner.isRunning();
    }

    @Override
    public BackendReadiness readiness(DispatcherTopics topics) {
        if (messaging.shared()) topics = messaging.topics();
        try {
            if (topics == null) {
                String clusterId = kafkaAdmin.clusterId();
                if (clusterId == null || clusterId.isBlank()) return BackendReadiness.down("Kafka Cluster ID 不可用");
                return BackendReadiness.up();
            }
            if (ensureTopics) {
                kafkaAdmin.createOrModifyTopics(topicsToEnsure(topics));
            }
            kafkaAdmin.describeTopics(
                    topics.commandTopic(), topics.runnerEventTopic(),
                    topics.adminEventTopic(), topics.runnerControlTopic());
            if (kafkaTemplate.partitionsFor(topics.adminEventTopic()).isEmpty()) {
                return BackendReadiness.down("Admin 事件 Topic 没有可用分区");
            }
            return BackendReadiness.up();
        } catch (RuntimeException exception) {
            return BackendReadiness.down("Kafka 或执行 Topic 不可用");
        }
    }

    static NewTopic[] topicsToEnsure(DispatcherTopics topics) {
        return new NewTopic[]{
                new NewTopic(topics.commandTopic(), 1, (short) 1),
                new NewTopic(topics.runnerEventTopic(), 1, (short) 1),
                new NewTopic(topics.adminEventTopic(), 1, (short) 1),
                new NewTopic(topics.runnerControlTopic(), 1, (short) 1)
        };
    }

    @Override
    public void run(ApplicationArguments args) {
        if (messaging.shared()) {
            var incompatible = registrationRepository.findAll().stream().anyMatch(row ->
                    row.getState() != cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState.INACTIVE
                    && !messaging.topics().equals(new DispatcherTopics(row.getCommandTopic(), row.getRunnerEventTopic(),
                            row.getAdminEventTopic(), row.getRunnerControlTopic())));
            if (incompatible) throw new IllegalStateException("存在旧消息通道注册，请完成维护窗口迁移后再启用实例共享通道");
            startShared();
            return;
        }
        registrationRepository.findAll().forEach(registration -> {
            if (registration.getState() == cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState.ACTIVE
                    || registration.getState() == cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState.DRAINING) {
                start(registration);
            }
        });
    }

    private synchronized void startShared() {
        var id = identity.instanceId();
        if (listenersRunning(id)) return;
        stopAll();
        var command = factory.createContainer(messaging.commandTopic());
        command.getContainerProperties().setGroupId("datascalpel-dispatcher-command-instance-" + id);
        command.setupMessageListener((MessageListener<String, String>) record -> commandReceiver.receiveShared(record, registrationRepository));
        var runner = factory.createContainer(messaging.runnerEventTopic());
        runner.getContainerProperties().setGroupId("datascalpel-dispatcher-runner-instance-" + id);
        runner.setupMessageListener((MessageListener<String, String>) record -> runnerReceiver.receiveShared(record, registrationRepository));
        commands.put(id, command);
        runners.put(id, runner);
        command.start();
        runner.start();
    }
}
