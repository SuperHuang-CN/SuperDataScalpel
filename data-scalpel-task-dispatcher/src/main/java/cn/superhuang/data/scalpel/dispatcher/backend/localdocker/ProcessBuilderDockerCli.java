package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessBuilderDockerCli implements DockerCli {
    private static final byte[] TRUNCATION_MARKER =
            "\n--- DOCKER OUTPUT TRUNCATED: MIDDLE CONTENT OMITTED ---\n".getBytes(StandardCharsets.UTF_8);

    @Override
    public DockerCommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes)
            throws BackendException {
        if (arguments == null || arguments.isEmpty() || arguments.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BackendException("INVALID_DOCKER_COMMAND", "Docker 命令参数无效");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || maximumOutputBytes < 1024
                || maximumOutputBytes > Integer.MAX_VALUE) {
            throw new BackendException("INVALID_DOCKER_COMMAND", "Docker 命令限制无效");
        }
        Process process = null;
        Thread stdoutReader = null;
        Thread stderrReader = null;
        int stdoutLimit = (int) maximumOutputBytes / 2;
        int stderrLimit = (int) maximumOutputBytes - stdoutLimit;
        HeadTailOutput stdout = new HeadTailOutput(stdoutLimit);
        HeadTailOutput stderr = new HeadTailOutput(stderrLimit);
        try {
            process = new ProcessBuilder(List.copyOf(arguments)).start();
            Process running = process;
            stdoutReader = Thread.ofVirtual().name("dispatcher-docker-stdout")
                    .start(() -> read(running.getInputStream(), stdout));
            stderrReader = Thread.ofVirtual().name("dispatcher-docker-stderr")
                    .start(() -> read(running.getErrorStream(), stderr));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                join(stdoutReader);
                join(stderrReader);
                throw new BackendException("DOCKER_COMMAND_TIMEOUT", "Docker 命令执行超时");
            }
            join(stdoutReader);
            join(stderrReader);
            return new DockerCommandResult(
                    process.exitValue(), stdout.bytes(), stderr.bytes(), stdout.truncated(), stderr.truncated());
        } catch (IOException exception) {
            throw new BackendException("DOCKER_COMMAND_FAILED", "无法启动 Docker CLI", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new BackendException("DOCKER_COMMAND_INTERRUPTED", "Docker 命令被中断", exception);
        }
    }

    private static void read(InputStream input, HeadTailOutput output) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        } catch (IOException ignored) {
            // Process termination can close the pipe. The process exit status remains authoritative.
        }
    }

    private static void join(Thread reader) throws InterruptedException {
        if (reader == null) return;
        reader.join(5000);
        if (reader.isAlive()) reader.interrupt();
    }

    static final class HeadTailOutput {
        private final int maximumBytes;
        private final int headLimit;
        private final byte[] tail;
        private final ByteArrayOutputStream initial;
        private byte[] head;
        private int tailPosition;
        private int tailSize;
        private boolean truncated;

        HeadTailOutput(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            int payload = Math.max(2, maximumBytes - TRUNCATION_MARKER.length);
            this.headLimit = payload / 2;
            this.tail = new byte[payload - headLimit];
            this.initial = new ByteArrayOutputStream(Math.min(maximumBytes, 64 * 1024));
        }

        synchronized void write(byte[] value, int offset, int length) {
            if (!truncated && initial.size() + length <= maximumBytes) {
                initial.write(value, offset, length);
                return;
            }
            if (!truncated) switchToTruncated(value, offset, length);
            else appendTail(value, offset, length);
        }

        private void switchToTruncated(byte[] value, int offset, int length) {
            byte[] existing = initial.toByteArray();
            head = new byte[Math.min(headLimit, existing.length + length)];
            int copiedExisting = Math.min(head.length, existing.length);
            System.arraycopy(existing, 0, head, 0, copiedExisting);
            if (copiedExisting < head.length) {
                System.arraycopy(value, offset, head, copiedExisting, head.length - copiedExisting);
            }
            if (existing.length > head.length) appendTail(existing, head.length, existing.length - head.length);
            int consumedNew = Math.max(0, head.length - existing.length);
            if (length > consumedNew) appendTail(value, offset + consumedNew, length - consumedNew);
            truncated = true;
        }

        private void appendTail(byte[] value, int offset, int length) {
            if (tail.length == 0) return;
            for (int index = 0; index < length; index++) {
                tail[tailPosition] = value[offset + index];
                tailPosition = (tailPosition + 1) % tail.length;
                if (tailSize < tail.length) tailSize++;
            }
        }

        synchronized byte[] bytes() {
            if (!truncated) return initial.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream(maximumBytes);
            result.writeBytes(head);
            result.writeBytes(TRUNCATION_MARKER);
            int start = tailSize == tail.length ? tailPosition : 0;
            for (int index = 0; index < tailSize; index++) {
                result.write(tail[(start + index) % tail.length]);
            }
            return result.toByteArray();
        }

        synchronized boolean truncated() { return truncated; }
    }
}
