package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.List;

public record DataModelDetailResponse(
        DataModelResponse model,
        List<DataModelFieldResponse> fields
) {
}
