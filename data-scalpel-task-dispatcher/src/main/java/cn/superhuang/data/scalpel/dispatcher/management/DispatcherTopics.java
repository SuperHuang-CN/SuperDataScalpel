package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DispatcherTopics(
        @NotBlank @Size(max = 249) String commandTopic,
        @NotBlank @Size(max = 249) String runnerEventTopic,
        @NotBlank @Size(max = 249) String adminEventTopic,
        @NotBlank @Size(max = 249) String runnerControlTopic
) {
    public DispatcherTopics {
        runnerControlTopic = runnerControlTopic == null || runnerControlTopic.isBlank()
                ? runnerEventTopic + ".control"
                : runnerControlTopic.trim();
    }

    public DispatcherTopics(String commandTopic, String runnerEventTopic, String adminEventTopic) {
        this(commandTopic, runnerEventTopic, adminEventTopic, runnerEventTopic + ".control");
    }
}
