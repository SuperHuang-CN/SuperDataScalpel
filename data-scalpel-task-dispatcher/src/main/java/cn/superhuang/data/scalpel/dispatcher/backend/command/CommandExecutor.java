package cn.superhuang.data.scalpel.dispatcher.backend.command;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;

import java.time.Duration;
import java.util.List;

public interface CommandExecutor {
    CommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes) throws BackendException;
}
