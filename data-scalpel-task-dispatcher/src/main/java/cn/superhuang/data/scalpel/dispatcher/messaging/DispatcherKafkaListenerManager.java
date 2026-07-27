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
    private ConcurrentMessageListenerContainer<String, String> commandContainer;
    private ConcurrentMessageListenerContainer<String, String> runnerContainer;

    public DispatcherKafkaListenerManager(
            ConcurrentKafkaListenerContainerFactory<String, String> factory,
            DispatcherCommandRecordReceiver commandReceiver,
            DispatcherRunnerRecordReceiver runnerReceiver,
            cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository registrationRepository,
            KafkaAdmin kafkaAdmin,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${data-scalpel.dispatcher.ensure-topics:false}") boolean ensureTopics
    ) {
        this.factory = factory;
        this.commandReceiver = commandReceiver;
        this.runnerReceiver = runnerReceiver;
        this.registrationRepository = registrationRepository;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
        this.ensureTopics = ensureTopics;
    }

    @Override
    public synchronized void start(DispatcherRegistration registration) {
        stopAll();
        String suffix = registration.getDispatcherInstanceId().toString();
        commandContainer = factory.createContainer(registration.getCommandTopic());
        commandContainer.getContainerProperties().setGroupId("datascalpel-dispatcher-command-" + suffix);
        commandContainer.setupMessageListener((MessageListener<String, String>) commandReceiver::receive);
        runnerContainer = factory.createContainer(registration.getRunnerEventTopic());
        runnerContainer.getContainerProperties().setGroupId("datascalpel-dispatcher-runner-" + suffix);
        runnerContainer.setupMessageListener((MessageListener<String, String>) runnerReceiver::receive);
        commandContainer.start();
        runnerContainer.start();
    }

    @Override
    public synchronized void stopCommandListener() {
        if (commandContainer != null) commandContainer.stop();
        commandContainer = null;
    }

    @Override
    public synchronized void stopAll() {
        if (commandContainer != null) commandContainer.stop();
        if (runnerContainer != null) runnerContainer.stop();
        commandContainer = null;
        runnerContainer = null;
    }

    @Override
    public synchronized boolean listenersRunning() {
        return commandContainer != null && commandContainer.isRunning()
                && runnerContainer != null && runnerContainer.isRunning();
    }

    @Override
    public BackendReadiness readiness(DispatcherTopics topics) {
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
        registrationRepository.findFirstByOrderByCreatedAtAsc().ifPresent(registration -> {
            if (registration.getState() == cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState.ACTIVE
                    || registration.getState() == cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState.DRAINING) {
                start(registration);
            }
        });
    }
}
