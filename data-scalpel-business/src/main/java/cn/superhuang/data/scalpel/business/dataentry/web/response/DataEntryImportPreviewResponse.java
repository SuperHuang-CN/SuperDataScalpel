package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DataEntryImportPreviewResponse(
        String fileName,
        DataEntryImportFormat format,
        long fileSize,
        int totalRowCount,
        int validRowCount,
        int errorRowCount,
        int issueCount,
        boolean importable,
        boolean issuesTruncated,
        String previewDigest,
        List<Field> fields,
        List<Row> previewRows,
        List<Issue> issues
) {
    public DataEntryImportPreviewResponse {
        fields = List.copyOf(fields);
        previewRows = List.copyOf(previewRows);
        issues = List.copyOf(issues);
    }

    public record Field(
            UUID id,
            String code,
            String name,
            PlatformDataType fieldType,
            boolean nullable,
            boolean primaryKey,
            String inputSource
    ) {
    }

    public record Row(
            int rowNumber,
            Map<String, Object> values,
            Map<String, String> displayValues
    ) {
        public Row {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            displayValues = Collections.unmodifiableMap(new LinkedHashMap<>(displayValues));
        }
    }

    public record Issue(
            String code,
            int rowNumber,
            String fieldCode,
            String message
    ) {
    }
}
