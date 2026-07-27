package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

final class RunnerLaunchLoader {
    private static final int MAXIMUM_LAUNCH_BYTES = 1024 * 1024;
    private static final String LAUNCH_FILE_ENV = "DATASCALPEL_TASK_LAUNCH_FILE";
    private static final String WORK_DIRECTORY_ENV = "DATASCALPEL_TASK_WORK_DIRECTORY";
    private final ObjectMapper objectMapper;

    RunnerLaunchLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    LoadedLaunch load(Map<String, String> environment) throws Exception {
        String configured = environment.get(LAUNCH_FILE_ENV);
        Path path = configured == null || configured.isBlank() ? Path.of("launch.json") : Path.of(configured.trim());
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute) || Files.size(absolute) < 1 || Files.size(absolute) > MAXIMUM_LAUNCH_BYTES) {
            throw new RunnerExecutionException("INVALID_LAUNCH", "launch.json 不存在或大小无效", null);
        }
        byte[] content = Files.readAllBytes(absolute);
        TaskExecutionLaunchDescriptor launch;
        try {
            launch = objectMapper.readValue(content, TaskExecutionLaunchDescriptor.class);
        } catch (Exception exception) {
            throw new RunnerExecutionException("INVALID_LAUNCH", "launch.json 协议无效", null, exception);
        }
        if (launch.deadlineAt() != null && !launch.deadlineAt().isAfter(Instant.now())) {
            throw new RunnerExecutionException("EXECUTION_DEADLINE_EXCEEDED", "任务已超过执行截止时间", null);
        }
        String configuredWorkDirectory = environment.get(WORK_DIRECTORY_ENV);
        Path workDirectory = configuredWorkDirectory == null || configuredWorkDirectory.isBlank()
                ? absolute.getParent()
                : Path.of(configuredWorkDirectory.trim()).toAbsolutePath().normalize();
        if (Files.exists(workDirectory) && !Files.isDirectory(workDirectory)) {
            throw new RunnerExecutionException("INVALID_WORK_DIRECTORY", "Runner 工作目录不是目录", null);
        }
        return new LoadedLaunch(launch, absolute, workDirectory);
    }

    record LoadedLaunch(TaskExecutionLaunchDescriptor descriptor, Path path, Path workDirectory) {
        LoadedLaunch {
            if (descriptor == null || path == null || workDirectory == null) {
                throw new IllegalArgumentException("Runner 启动文件无效");
            }
        }
    }
}
