package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.List;

public record ModelMetadataImportResultResponse(
        int modelCount,
        int fieldCount,
        List<ImportedModelMetadataResponse> models
) {

    public ModelMetadataImportResultResponse {
        models = List.copyOf(models);
    }
}
