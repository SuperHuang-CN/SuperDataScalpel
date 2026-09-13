package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;

@Schema(description = "文件解析后形成的一个逻辑字段及其数据库无关类型约束。")

public record FileDatasetFieldResponse(
        @Schema(description = "字段名称，同时作为任务 Canvas 读取列名。")
        String name,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "字段的平台数据类型。")
        PlatformDataType fieldType,
        @Schema(description = "STRING 或 BINARY 字段允许的最大长度；其他平台类型为空。")
        Integer length,
        @Schema(description = "DECIMAL 字段的总有效位数；其他平台类型为空。")
        Integer precision,
        @Schema(description = "DECIMAL 字段的小数位数；其他平台类型为空。")
        Integer scale,
        @Schema(description = "字段是否允许保存空值。")
        boolean nullable,
        @Schema(description = "字段的平台类型定义，包含长度、精度或空间属性。")
        PlatformTypeDefinition platformTypeDefinition
) {

    public static FileDatasetFieldResponse from(FileDatasetField field) {
        return new FileDatasetFieldResponse(
                field.getName(),
                field.getSortOrder(),
                field.getFieldType(),
                field.getLength(),
                field.getPrecision(),
                field.getScale(),
                field.isNullable(),
                field.getTypeDefinition()
        );
    }

    public static List<FileDatasetFieldResponse> from(List<FileDatasetField> fields) {
        return fields.stream().map(FileDatasetFieldResponse::from).toList();
    }
}
