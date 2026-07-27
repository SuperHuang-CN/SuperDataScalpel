package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.UUID;

public record TaskModelReferenceLocationResponse(
        ModelTaskRelationRole role,
        ModelTaskReferenceType referenceType,
        Integer ordinal,
        UUID nodeId,
        String nodeName
) {
}
