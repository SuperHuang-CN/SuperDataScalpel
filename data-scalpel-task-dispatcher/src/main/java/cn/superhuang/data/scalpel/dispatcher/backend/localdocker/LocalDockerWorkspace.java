package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import java.nio.file.Path;

public record LocalDockerWorkspace(Path directory, Path launchFile, Path environmentFile) {
    public LocalDockerWorkspace {
        if (directory == null || launchFile == null || environmentFile == null) {
            throw new IllegalArgumentException("Local Docker 工作目录无效");
        }
    }
}
