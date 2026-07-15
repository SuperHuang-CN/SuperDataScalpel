package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.util.List;

public record FileDatasetFieldResponse(
        String name,
        int sortOrder,
        LogicalType logicalType,
        boolean nullable
) {

    public static FileDatasetFieldResponse from(FileDatasetField field) {
        return new FileDatasetFieldResponse(field.getName(), field.getSortOrder(), field.getLogicalType(), field.isNullable());
    }

    public static List<FileDatasetFieldResponse> from(List<FileDatasetField> fields) {
        return fields.stream().map(FileDatasetFieldResponse::from).toList();
    }
}
