package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Schema(description = "对完整 Excel/CSV 文件只读校验后的有限预览与确认摘要")

public record DataEntryImportPreviewResponse(
        @Schema(description = "文件显示名称。")
        String fileName,
        @Schema(description = "本次解析采用的导入模板格式：XLSX 或 CSV。")
        DataEntryImportFormat format,
        @Schema(description = "文件大小，单位字节。")
        long fileSize,
        @Schema(description = "文件中的非空数据行总数，不含两行表头。")
        int totalRowCount,
        @Schema(description = "通过字段类型、必填、字典和关联模型校验的数据行数。")
        int validRowCount,
        @Schema(description = "至少包含一项阻断问题、不能确认导入的数据行数。")
        int errorRowCount,
        @Schema(description = "完整文件扫描发现的问题总数，可能大于 issues 实际返回条数。")
        int issueCount,
        @Schema(description = "当前文件是否没有任何问题并可提交确认导入。")
        boolean importable,
        @Schema(description = "实际问题是否超过响应最多返回的 200 条。")
        boolean issuesTruncated,
        @Schema(description = "覆盖文件内容、表单、模型版本、字段、码表和关联配置的 SHA-256；确认导入时必须原样提交。")
        String previewDigest,
        @Schema(description = "确认导入所依据的目标模型字段快照，按表单字段顺序排列。")
        List<Field> fields,
        @Schema(description = "文件前 100 条数据行的规范化预览；完整文件仍已被扫描。")
        List<Row> previewRows,
        @Schema(description = "校验、健康检查或导入问题列表。")
        List<Issue> issues
) {
    public DataEntryImportPreviewResponse {
        fields = List.copyOf(fields);
        previewRows = List.copyOf(previewRows);
        issues = List.copyOf(issues);
    }

    @Schema(description = "当前模型字段及其导入输入来源")

    public record Field(
            @Schema(description = "目标数据模型字段 UUID。")
            UUID id,
            @Schema(description = "模型字段稳定编码，对应文件第一行字段编码并作为 values 的键。")
            String code,
            @Schema(description = "模型字段显示名称，对应文件第二行表头。")
            String name,
            @Schema(description = "字段的平台数据类型。")
            PlatformDataType fieldType,
            @Schema(description = "字段是否允许保存空值。")
            boolean nullable,
            @Schema(description = "是否为主键字段。")
            boolean primaryKey,
            @Schema(description = "字段输入方式：DEFAULT 普通值，DICTIONARY 标准字典码值，MODEL_LOOKUP 关联模型键值。")
            String inputSource
    ) {
    }

    @Schema(description = "一条规范化预览数据行")

    public record Row(
            @Schema(description = "原文件中的一基行号，包含两行表头后的实际位置。")
            int rowNumber,
            @Schema(description = "按字段编码传递或返回的值映射。")
            Map<String, Object> values,
            @Schema(description = "按字段编码返回的格式化展示值。")
            Map<String, String> displayValues
    ) {
        public Row {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            displayValues = Collections.unmodifiableMap(new LinkedHashMap<>(displayValues));
        }
    }

    @Schema(description = "文件级、行级或字段级校验问题")

    public record Issue(
            @Schema(description = "稳定问题码，用于区分文件结构、字段取值、字典和关联校验错误。")
            String code,
            @Schema(description = "问题所在原文件行号；文件级问题为 0。")
            int rowNumber,
            @Schema(description = "问题对应的模型字段编码；非字段问题为空。")
            String fieldCode,
            @Schema(description = "说明该文件、行或字段为何不能导入的可读校验信息。")
            String message
    ) {
    }
}
