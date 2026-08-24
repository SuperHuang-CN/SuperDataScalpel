package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageExternalResourceType;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.util.UUID;
import java.util.List;

public record LineageGraphNodeResponse(
        String id,
        LineageGraphNodeKind kind,
        LineageGraphNodeSide side,
        int depth,
        String label,
        String subtitle,
        UUID modelId,
        UUID modelFieldId,
        UUID taskId,
        UUID dataSourceId,
        TaskStatus taskStatus,
        Integer definitionVersion,
        LineageWriteMode writeMode,
        boolean stale,
        LineageExternalResourceType externalResourceType,
        UUID resourceId,
        UUID dataServiceId,
        DataServiceType dataServiceType,
        DataServiceStatus dataServiceStatus,
        String routePath,
        LineageFieldOwnerResponse fieldOwner,
        boolean focusRoot,
        List<String> focusFieldKeys
) {
    public LineageGraphNodeResponse {
        focusFieldKeys = focusFieldKeys == null ? List.of() : List.copyOf(focusFieldKeys);
    }

    public LineageGraphNodeResponse(
            String id, LineageGraphNodeKind kind, LineageGraphNodeSide side, int depth,
            String label, String subtitle, UUID modelId, UUID modelFieldId, UUID taskId,
            UUID dataSourceId, TaskStatus taskStatus, Integer definitionVersion,
            LineageWriteMode writeMode, boolean stale, LineageExternalResourceType externalResourceType,
            UUID resourceId, UUID dataServiceId, DataServiceType dataServiceType,
            DataServiceStatus dataServiceStatus, String routePath
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                externalResourceType, resourceId, dataServiceId, dataServiceType,
                dataServiceStatus, routePath, null, false, List.of());
    }
    public LineageGraphNodeResponse(
            String id,
            LineageGraphNodeKind kind,
            LineageGraphNodeSide side,
            int depth,
            String label,
            String subtitle,
            UUID modelId,
            UUID modelFieldId,
            UUID taskId,
            UUID dataSourceId,
            TaskStatus taskStatus,
            Integer definitionVersion,
            LineageWriteMode writeMode,
            boolean stale,
            LineageExternalResourceType externalResourceType,
            UUID resourceId
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                externalResourceType, resourceId, null, null, null, null, null, false, List.of());
    }

    public LineageGraphNodeResponse(
            String id,
            LineageGraphNodeKind kind,
            LineageGraphNodeSide side,
            int depth,
            String label,
            String subtitle,
            UUID modelId,
            UUID modelFieldId,
            UUID taskId,
            UUID dataSourceId,
            TaskStatus taskStatus,
            Integer definitionVersion,
            LineageWriteMode writeMode,
            boolean stale
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                null, null, null, null, null, null, null, false, List.of());
    }
}
