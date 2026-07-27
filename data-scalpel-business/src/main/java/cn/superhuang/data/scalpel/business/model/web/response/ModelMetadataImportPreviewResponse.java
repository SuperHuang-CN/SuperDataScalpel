package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.List;

public record ModelMetadataImportPreviewResponse(
        String fileName,
        int formatVersion,
        boolean importable,
        List<String> issues,
        List<ModelMetadataImportModelResponse> models
) {

    public ModelMetadataImportPreviewResponse {
        issues = List.copyOf(issues);
        models = List.copyOf(models);
    }
}
