package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "模型元数据整批写入管理库后的结果；所有模型均为 MANAGED + DRAFT，尚未创建物理表、发布或导入业务数据。")
public record ModelMetadataImportResultResponse(
        @Schema(description = "本次创建的 MANAGED + DRAFT 模型数量") int modelCount,
        @Schema(description = "本次创建的模型字段总数") int fieldCount,
        @Schema(description = "本次创建的模型摘要") List<ImportedModelMetadataResponse> models
) {

    public ModelMetadataImportResultResponse {
        models = List.copyOf(models);
    }
}
