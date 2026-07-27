package cn.superhuang.data.scalpel.dispatcher.backend;

public record BackendLog(byte[] content, boolean truncated) {
    public BackendLog {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() { return content.clone(); }
}
