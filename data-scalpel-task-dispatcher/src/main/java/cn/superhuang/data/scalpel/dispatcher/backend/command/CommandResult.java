package cn.superhuang.data.scalpel.dispatcher.backend.command;

import java.nio.charset.StandardCharsets;

public record CommandResult(int exitCode, byte[] output, boolean truncated) {
    public CommandResult {
        output = output == null ? new byte[0] : output.clone();
    }

    @Override
    public byte[] output() { return output.clone(); }

    public boolean successful() { return exitCode == 0; }

    public String outputText() { return new String(output, StandardCharsets.UTF_8).trim(); }
}
