package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "码表 Excel 的只读解析和整批变更预览；预览不会保存数据，提交时会重新解析文件并校验摘要与现有码表版本")
public record StandardDictionaryImportPreviewResponse(
        @Schema(description = "上传时经过安全处理的原始文件名，仅用于展示") String fileName,
        @Schema(description = "识别到的码表 Excel 模板格式版本；当前为 1") int formatVersion,
        @Schema(description = "整个工作簿当前是否通过校验并允许提交；任一错误都会为 false") boolean importable,
        @Schema(description = "文件规范化内容、匹配到的码表 UUID 与内容版本以及当前校验问题的 SHA-256 摘要；提交同一文件时必须原样回传。涉及的码表版本、匹配结果或会改变校验结论的引用变化可导致摘要冲突，并非数据库任意无关变化都会影响摘要") String previewDigest,
        @Schema(description = "工作簿级格式、引用或完整性问题；非空时 importable 为 false，整批禁止导入") List<String> issues,
        @Schema(description = "按工作簿码表行返回的规范化内容、逐节点结果和预计动作；必须至少包含一张码表且所有码表和节点均通过校验才可提交") List<StandardDictionaryImportDictionaryPreviewResponse> dictionaries
) {
    public StandardDictionaryImportPreviewResponse {
        issues = List.copyOf(issues);
        dictionaries = List.copyOf(dictionaries);
    }
}
