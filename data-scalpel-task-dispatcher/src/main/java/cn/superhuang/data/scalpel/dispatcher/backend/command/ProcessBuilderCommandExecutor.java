package cn.superhuang.data.scalpel.dispatcher.backend.command;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class ProcessBuilderCommandExecutor implements CommandExecutor {
    private static final byte[] MARKER =
            "\n--- COMMAND OUTPUT TRUNCATED: MIDDLE CONTENT OMITTED ---\n".getBytes(StandardCharsets.UTF_8);

    @Override
    public CommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes)
            throws BackendException {
        if (arguments == null || arguments.isEmpty()
                || arguments.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BackendException("INVALID_BACKEND_COMMAND", "Backend 命令参数无效");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || maximumOutputBytes < 1024 || maximumOutputBytes > Integer.MAX_VALUE) {
            throw new BackendException("INVALID_BACKEND_COMMAND", "Backend 命令限制无效");
        }
        Process process = null;
        Thread reader = null;
        BoundedOutput output = new BoundedOutput((int) maximumOutputBytes);
        try {
            process = new ProcessBuilder(List.copyOf(arguments)).redirectErrorStream(true).start();
            Process running = process;
            reader = Thread.ofVirtual().name("dispatcher-backend-command-output")
                    .start(() -> read(running.getInputStream(), output));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                join(reader);
                throw new BackendException("BACKEND_COMMAND_TIMEOUT", "Backend 命令执行超时");
            }
            join(reader);
            return new CommandResult(process.exitValue(), output.bytes(), output.truncated());
        } catch (IOException exception) {
            throw new BackendException("BACKEND_COMMAND_FAILED", "无法启动 Backend 命令", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new BackendException("BACKEND_COMMAND_INTERRUPTED", "Backend 命令被中断", exception);
        }
    }

    private static void read(InputStream input, BoundedOutput output) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
        } catch (IOException ignored) {
            // The process exit status remains authoritative when termination closes the pipe.
        }
    }

    private static void join(Thread reader) throws InterruptedException {
        if (reader == null) return;
        reader.join(5000);
        if (reader.isAlive()) reader.interrupt();
    }

    static final class BoundedOutput {
        private final int maximumBytes;
        private final int headLimit;
        private final byte[] tail;
        private final ByteArrayOutputStream initial;
        private byte[] head;
        private int tailPosition;
        private int tailSize;
        private boolean truncated;

        BoundedOutput(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            int payload = Math.max(2, maximumBytes - MARKER.length);
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
            int copied = Math.min(head.length, existing.length);
            System.arraycopy(existing, 0, head, 0, copied);
            if (copied < head.length) System.arraycopy(value, offset, head, copied, head.length - copied);
            if (existing.length > head.length) appendTail(existing, head.length, existing.length - head.length);
            int consumed = Math.max(0, head.length - existing.length);
            if (length > consumed) appendTail(value, offset + consumed, length - consumed);
            truncated = true;
        }

        private void appendTail(byte[] value, int offset, int length) {
            for (int index = 0; index < length && tail.length > 0; index++) {
                tail[tailPosition] = value[offset + index];
                tailPosition = (tailPosition + 1) % tail.length;
                if (tailSize < tail.length) tailSize++;
            }
        }

        synchronized byte[] bytes() {
            if (!truncated) return initial.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream(maximumBytes);
            result.writeBytes(head);
            result.writeBytes(MARKER);
            int start = tailSize == tail.length ? tailPosition : 0;
            for (int index = 0; index < tailSize; index++) result.write(tail[(start + index) % tail.length]);
            return result.toByteArray();
        }

        synchronized boolean truncated() { return truncated; }
    }
}
