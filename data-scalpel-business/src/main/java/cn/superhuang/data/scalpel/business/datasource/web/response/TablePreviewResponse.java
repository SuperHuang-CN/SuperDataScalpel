package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TablePreview;

import java.util.List;

public record TablePreviewResponse(
        TableIdentifierResponse table,
        List<PreviewColumnResponse> columns,
        List<List<Object>> rows,
        int limit,
        boolean truncated
) {
    public static TablePreviewResponse from(TablePreview preview) {
        return new TablePreviewResponse(
                TableIdentifierResponse.from(preview.table()),
                preview.columns().stream().map(PreviewColumnResponse::from).toList(),
                preview.rows(),
                preview.limit(),
                preview.truncated()
        );
    }
}
