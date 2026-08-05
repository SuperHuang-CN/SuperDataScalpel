package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;

public record FileDatasetFieldResponse(
        String name,
        int sortOrder,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
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
