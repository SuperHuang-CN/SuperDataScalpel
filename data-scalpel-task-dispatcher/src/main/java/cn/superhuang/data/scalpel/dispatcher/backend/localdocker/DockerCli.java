package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;

import java.time.Duration;
import java.util.List;

public interface DockerCli {
    DockerCommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes)
            throws BackendException;
}
