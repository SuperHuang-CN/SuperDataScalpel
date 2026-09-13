package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "模型元数据 Excel 的只读解析与校对预览。格式、工作表或表头错误直接返回 HTTP 错误；可继续校对的模型与字段错误进入 issues。会连接目标数据源读取表存在性并规划空间建表能力，但不读取业务行、不保存模型、不执行 DDL。")
public record ModelMetadataImportPreviewResponse(
        @Schema(description = "本次只读解析的 Excel 文件名") String fileName,
        @Schema(description = "识别到的模型元数据模板版本，当前兼容 1 至 5：V2 增加空间参数，V3 增加数仓分层，V4 增加码表，V5 增加模型目录路径。") int formatVersion,
        @Schema(description = "文件结构及全部模型、字段当前是否通过校验") boolean importable,
        @Schema(description = "可在预览中表达的文件级问题，例如模型为空、总字段超过 20,000、字段无法唯一关联模型或目录树异常；模板标识、版本、工作表和表头不合法时接口直接返回 400。") List<String> issues,
        @Schema(description = "逐模型、逐字段的规范化内容和校验结果") List<ModelMetadataImportModelResponse> models
) {

    public ModelMetadataImportPreviewResponse {
        issues = List.copyOf(issues);
        models = List.copyOf(models);
    }
}
