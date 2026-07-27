package cn.superhuang.data.scalpel.business.compute.client;

public record DispatcherTopics(
        String commandTopic,
        String runnerEventTopic,
        String adminEventTopic,
        String runnerControlTopic
) {
    public DispatcherTopics {
        runnerControlTopic = runnerControlTopic == null || runnerControlTopic.isBlank()
                ? runnerEventTopic + ".control"
                : runnerControlTopic;
    }

    public DispatcherTopics(String commandTopic, String runnerEventTopic, String adminEventTopic) {
        this(commandTopic, runnerEventTopic, adminEventTopic, runnerEventTopic + ".control");
    }
}
