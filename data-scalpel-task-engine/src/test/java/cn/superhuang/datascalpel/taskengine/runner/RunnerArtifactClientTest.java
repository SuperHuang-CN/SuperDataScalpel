package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import static org.junit.jupiter.api.Assertions.*;

class RunnerArtifactClientTest {
    @Test void refusedArtifactConnectionIsNotReportedAsJdbcFailure() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        var client = new RunnerArtifactClient(HttpClient.newBuilder().proxy(new java.net.ProxySelector() {
            public java.util.List<java.net.Proxy> select(URI uri) { return java.util.List.of(java.net.Proxy.NO_PROXY); }
            public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException error) { }
        }).build());
        var uri = URI.create("http://127.0.0.1:" + port + "/artifact");
        var download = assertThrows(RunnerExecutionException.class, () -> client.download(uri, 1024));
        assertEquals("MANIFEST_DOWNLOAD_FAILED", download.code());
        var upload = assertThrows(RunnerExecutionException.class, () -> client.upload(uri, new byte[0], "application/json"));
        assertEquals("RESULT_UPLOAD_FAILED", upload.code());
        for (var failure : java.util.List.of(download, upload)) {
            var error = new RunnerFailureClassifier().classify(failure, RunnerFailureContext.task(ExecutionFailurePhase.PREPARE));
            assertEquals(failure.code(), error.code());
            assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, error.category());
            assertTrue(error.retryable());
        }
    }
}
