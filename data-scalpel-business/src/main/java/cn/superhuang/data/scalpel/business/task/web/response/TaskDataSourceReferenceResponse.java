package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;

import java.util.UUID;

public record TaskDataSourceReferenceResponse(UUID id, String code, String name, DataSourceType type) {

    public static TaskDataSourceReferenceResponse from(DataSource source) {
        return new TaskDataSourceReferenceResponse(source.getId(), source.getCode(), source.getName(), source.getType());
    }
}
