package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import java.nio.charset.StandardCharsets;

public record DockerCommandResult(
        int exitCode,
        byte[] stdout,
        byte[] stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated
) {
    public DockerCommandResult {
        stdout = stdout == null ? new byte[0] : stdout.clone();
        stderr = stderr == null ? new byte[0] : stderr.clone();
    }

    @Override
    public byte[] stdout() { return stdout.clone(); }

    @Override
    public byte[] stderr() { return stderr.clone(); }

    public String stdoutText() { return new String(stdout, StandardCharsets.UTF_8).trim(); }

    public String stderrText() { return new String(stderr, StandardCharsets.UTF_8).trim(); }

    public byte[] combinedOutput() {
        if (stderr.length == 0) return stdout();
        if (stdout.length == 0) return stderr();
        byte[] combined = new byte[stdout.length + 1 + stderr.length];
        System.arraycopy(stdout, 0, combined, 0, stdout.length);
        combined[stdout.length] = '\n';
        System.arraycopy(stderr, 0, combined, stdout.length + 1, stderr.length);
        return combined;
    }

    public boolean truncated() { return stdoutTruncated || stderrTruncated; }

    public boolean successful() { return exitCode == 0; }
}
