package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TaskRunnerMain {
    private static final ObjectMapper OBJECT_MAPPER = JsonSupport.strictObjectMapper();

    private TaskRunnerMain() {
    }

    public static void main(String[] args) {
        int code = run(System.getenv());
        if (code != 0) System.exit(code);
    }

    static int run(java.util.Map<String, String> environment) {
        TaskRunnerApplication application = new TaskRunnerApplication(
                OBJECT_MAPPER,
                new RunnerArtifactClient(),
                launch -> new KafkaRunnerEventPublisher(launch.runnerEvent(), OBJECT_MAPPER),
                new CanvasTaskExecutor()::execute,
                new StreamingCanvasTaskExecutor(OBJECT_MAPPER)::execute
        );
        return application.run(environment);
    }
}
