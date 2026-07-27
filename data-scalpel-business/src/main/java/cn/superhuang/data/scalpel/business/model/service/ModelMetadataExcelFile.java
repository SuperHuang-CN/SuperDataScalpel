package cn.superhuang.data.scalpel.business.model.service;

public record ModelMetadataExcelFile(String fileName, byte[] content) {

    public ModelMetadataExcelFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
