package cn.superhuang.data.scalpel.business.directory.service;

import java.util.Objects;

public record DirectoryExcelFile(String fileName, byte[] content) {

    public DirectoryExcelFile {
        Objects.requireNonNull(fileName, "fileName");
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
