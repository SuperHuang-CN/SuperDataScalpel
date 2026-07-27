package cn.superhuang.datascalpel.taskengine.runner;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class RunnerArtifactClient implements RunnerArtifactAccess {
    private final HttpClient httpClient;

    RunnerArtifactClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    RunnerArtifactClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public byte[] download(URI uri, int maximumBytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new RunnerExecutionException("MANIFEST_DOWNLOAD_FAILED", "manifest 下载失败", null);
        }
        try (InputStream input = response.body()) {
            byte[] content = input.readNBytes(maximumBytes + 1);
            if (content.length > maximumBytes) {
                throw new RunnerExecutionException("MANIFEST_TOO_LARGE", "manifest 超过允许大小", null);
            }
            return content;
        }
    }

    @Override
    public void upload(URI uri, byte[] content, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RunnerExecutionException("RESULT_UPLOAD_FAILED", "执行结果上传失败", null);
        }
    }
}
