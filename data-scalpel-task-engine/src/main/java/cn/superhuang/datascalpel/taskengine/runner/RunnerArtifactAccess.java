package cn.superhuang.datascalpel.taskengine.runner;

import java.net.URI;

interface RunnerArtifactAccess {
    byte[] download(URI uri, int maximumBytes) throws Exception;
    void upload(URI uri, byte[] content, String contentType) throws Exception;
}
