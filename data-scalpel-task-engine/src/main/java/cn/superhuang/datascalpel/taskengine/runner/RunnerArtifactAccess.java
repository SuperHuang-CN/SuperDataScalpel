package cn.superhuang.datascalpel.taskengine.runner;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

interface RunnerArtifactAccess {
    byte[] download(URI uri, int maximumBytes) throws Exception;
    default void downloadToFile(URI uri, long expectedBytes, Path target) throws Exception {
        if (expectedBytes > Integer.MAX_VALUE) throw new IllegalArgumentException("下载对象过大");
        Files.write(target, download(uri, Math.toIntExact(expectedBytes)));
    }
    void upload(URI uri, byte[] content, String contentType) throws Exception;
    default void upload(URI uri, byte[] content, String contentType, Duration timeout) throws Exception {
        upload(uri, content, contentType);
    }
}
