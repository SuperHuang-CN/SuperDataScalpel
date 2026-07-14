package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;

import java.util.List;

public record PrimaryKeyResponse(String name, List<String> columns) {
    static PrimaryKeyResponse from(PrimaryKeyMetadata key) {
        return key == null ? null : new PrimaryKeyResponse(key.name(), key.columns());
    }
}
