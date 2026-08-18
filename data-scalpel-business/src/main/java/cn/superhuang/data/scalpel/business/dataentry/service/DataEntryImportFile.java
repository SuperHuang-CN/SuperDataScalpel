package cn.superhuang.data.scalpel.business.dataentry.service;

public record DataEntryImportFile(
        String fileName,
        String contentType,
        byte[] content
) {
    public DataEntryImportFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
