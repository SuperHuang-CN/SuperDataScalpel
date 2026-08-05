package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;

import java.util.List;
import java.util.Map;

/** Attribute-only sample; geometries are deliberately excluded from the management preview. */
public record SpatialFeaturePreviewResponse(
        List<CanvasColumnSchema> columns,
        List<Map<String, Object>> rows,
        int limit,
        boolean truncated
) {
}
