package cn.superhuang.data.scalpel.dispatcher.backend.command;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface CommandExecutor {
    CommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes) throws BackendException;

    default CommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes,
                                  Map<String, String> environment) throws BackendException {
        if (environment.isEmpty()) return execute(arguments, timeout, maximumOutputBytes);
        throw new BackendException("BACKEND_ENVIRONMENT_UNSUPPORTED", "命令执行器不支持目标专用环境变量");
    }
}
