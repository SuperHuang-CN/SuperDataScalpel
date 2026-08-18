package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.List;
import java.util.UUID;

public record DataModelReferencesResponse(
        UUID modelId,
        boolean deletable,
        List<DataModelReferenceTaskResponse> tasks,
        List<DataModelReferenceServiceResponse> services
) {
    public DataModelReferencesResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        services = services == null ? List.of() : List.copyOf(services);
    }
}
