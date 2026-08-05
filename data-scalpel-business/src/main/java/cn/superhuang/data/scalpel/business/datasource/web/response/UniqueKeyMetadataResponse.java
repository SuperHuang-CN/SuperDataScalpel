package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.UniqueKeyMetadata;

import java.util.List;

public record UniqueKeyMetadataResponse(String name, String type, List<String> columns) {
    static UniqueKeyMetadataResponse from(UniqueKeyMetadata key) {
        return new UniqueKeyMetadataResponse(key.name(), key.kind().name(), key.columns());
    }
}
