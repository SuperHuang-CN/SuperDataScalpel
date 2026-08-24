package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.assistant.service.TaskCanvasPlan;

import java.util.UUID;

public record AssistantClientActionResponse(
        AssistantClientActionType type,
        String pageKey,
        Boolean collapsed,
        DirectoryScope scope,
        UUID dataSourceId,
        String dataSourceName,
        AssistantDataSourceDraftResponse dataSourceDraft,
        UUID changeSetId,
        UUID taskId,
        TaskCanvasPlan.NewTaskDraft taskDraft
) {
    public static AssistantClientActionResponse navigate(String pageKey) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.NAVIGATE_PAGE, pageKey, null, null, null, null, null,
                null, null, null
        );
    }

    public static AssistantClientActionResponse sidebar(boolean collapsed) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.SET_APP_SIDEBAR_COLLAPSED, null, collapsed, null, null, null, null,
                null, null, null
        );
    }

    public static AssistantClientActionResponse export(DirectoryScope scope) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.DOWNLOAD_DIRECTORY_EXPORT, null, null, scope, null, null, null,
                null, null, null
        );
    }

    public static AssistantClientActionResponse createDataSource(AssistantDataSourceDraftResponse draft) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.OPEN_DATA_SOURCE_CREATE, null, null, null, null, null, draft,
                null, null, null
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
                draft,
                null,
                null,
                null
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
                null,
                null,
                null,
                null
        );
    }

    public static AssistantClientActionResponse taskCanvasProposal(
            UUID changeSetId,
            UUID taskId,
            TaskCanvasPlan.NewTaskDraft taskDraft
    ) {
        return new AssistantClientActionResponse(
                AssistantClientActionType.OPEN_TASK_CANVAS_PROPOSAL,
                null, null, null, null, null, null,
                changeSetId, taskId, taskDraft
        );
    }
}
