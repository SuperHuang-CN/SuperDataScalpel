package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;

import java.util.List;

public record IndexMetadataResponse(String name, boolean unique, List<String> columns) {
    static IndexMetadataResponse from(IndexMetadata index) {
        return new IndexMetadataResponse(index.name(), index.unique(), index.columns());
    }
}
