package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherIdentityService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatcherSharedListenerTest {
    @Test @SuppressWarnings("unchecked")
    void onePairPerInstanceSurvivesEngineStopAndReusesStableGroups() {
        var factory = (ConcurrentKafkaListenerContainerFactory<String, String>) mock(ConcurrentKafkaListenerContainerFactory.class);
        var command = (ConcurrentMessageListenerContainer<String, String>) mock(ConcurrentMessageListenerContainer.class);
        var runner = (ConcurrentMessageListenerContainer<String, String>) mock(ConcurrentMessageListenerContainer.class);
        var commandProperties = new ContainerProperties("commands.shared");
        var runnerProperties = new ContainerProperties("runners.shared");
        when(command.getContainerProperties()).thenReturn(commandProperties);
        when(runner.getContainerProperties()).thenReturn(runnerProperties);
        doAnswer(call -> { when(command.isRunning()).thenReturn(true); return null; }).when(command).start();
        doAnswer(call -> { when(runner.isRunning()).thenReturn(true); return null; }).when(runner).start();
        when(factory.createContainer("commands.shared")).thenReturn(command);
        when(factory.createContainer("runners.shared")).thenReturn(runner);
        var identity = mock(DispatcherIdentityService.class);
        var instanceId = UUID.randomUUID();
        when(identity.instanceId()).thenReturn(instanceId);
        var config = new DispatcherMessagingProperties(DispatcherMessagingProperties.Scope.INSTANCE, "commands.shared", "runners.shared", null);
        var manager = new DispatcherKafkaListenerManager(factory, null, null, mock(DispatcherRegistrationRepository.class), null, null, false, config, identity);
        var engineA = mock(DispatcherRegistration.class);
        var engineB = mock(DispatcherRegistration.class);
        manager.start(engineA);
        manager.start(engineB);
        manager.stop(UUID.randomUUID());
        assertThat(manager.listenersRunning(UUID.randomUUID())).isTrue();
        verify(factory, times(1)).createContainer("commands.shared");
        verify(factory, times(1)).createContainer("runners.shared");
        verify(command, never()).stop();
        verify(runner, never()).stop();
        assertThat(commandProperties.getGroupId()).isEqualTo("datascalpel-dispatcher-command-instance-" + instanceId);
        assertThat(runnerProperties.getGroupId()).isEqualTo("datascalpel-dispatcher-runner-instance-" + instanceId);
        manager.stopAll();
        verify(command).stop(); verify(runner).stop();
    }
}
