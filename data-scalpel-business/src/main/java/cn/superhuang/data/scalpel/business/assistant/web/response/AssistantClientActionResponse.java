package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.util.UUID;

public record AssistantClientActionResponse(
        AssistantClientActionType type,
        String pageKey,
        Boolean collapsed,
        DirectoryScope scope,
        UUID dataSourceId,
        String dataSourceName,
        AssistantDataSourceDraftResponse dataSourceDraft
) {
    public static AssistantClientActionResponse navigate(String pageKey) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.NAVIGATE_PAGE, pageKey, null, null, null, null, null
        );
    }

    public static AssistantClientActionResponse sidebar(boolean collapsed) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.SET_APP_SIDEBAR_COLLAPSED, null, collapsed, null, null, null, null
        );
    }

    public static AssistantClientActionResponse export(DirectoryScope scope) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.DOWNLOAD_DIRECTORY_EXPORT, null, null, scope, null, null, null
        );
    }

    public static AssistantClientActionResponse createDataSource(AssistantDataSourceDraftResponse draft) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.OPEN_DATA_SOURCE_CREATE, null, null, null, null, null, draft
        );
    }

    public static AssistantClientActionResponse editDataSource(
            UUID dataSourceId,
            String dataSourceName,
            AssistantDataSourceDraftResponse draft
    ) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.OPEN_DATA_SOURCE_EDIT,
                null,
                null,
                null,
                dataSourceId,
                dataSourceName,
                draft
        );
    }

    public static AssistantClientActionResponse testDataSource(UUID dataSourceId, String dataSourceName) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.CONFIRM_DATA_SOURCE_TEST,
                null,
                null,
                null,
                dataSourceId,
                dataSourceName,
                null
        );
    }
}
