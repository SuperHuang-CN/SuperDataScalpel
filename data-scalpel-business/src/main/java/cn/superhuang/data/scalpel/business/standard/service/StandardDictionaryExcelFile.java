package cn.superhuang.data.scalpel.business.standard.service;

public record StandardDictionaryExcelFile(String fileName, byte[] content) {
    public StandardDictionaryExcelFile {
        content = content.clone();
    }
}
