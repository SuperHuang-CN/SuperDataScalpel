package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;

import java.util.Set;
import java.util.UUID;

public record AssistantDataSourceDraftResponse(
        AssistantDataSourceDraftMode mode,
        String code,
        String name,
        UUID directoryId,
        Set<DataSourcePurpose> purposes,
        DataSourceType type,
        boolean enabled,
        String description
) {
    public AssistantDataSourceDraftResponse {
        if (mode == null) throw new IllegalArgumentException("数据源草稿模式不能为空");
        purposes = Set.copyOf(purposes);
    }
}
