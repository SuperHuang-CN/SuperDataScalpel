package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Attribute-only sample; geometries are deliberately excluded from the management preview. */
@Schema(description = "空间要素属性样例；管理预览刻意排除几何值")
public record SpatialFeaturePreviewResponse(
        @Schema(description = "属性字段及平台类型，不包含几何字段数据") List<CanvasColumnSchema> columns,
        @Schema(description = "按远端属性字段名组织的真实样例行，不包含几何值；不会按本地 Schema 做类型转换或数据脱敏，调用方必须按业务数据敏感性控制展示和传播") List<Map<String, Object>> rows,
        @Schema(description = "本次请求实际采用的要素数量上限") int limit,
        @Schema(description = "是否还存在未返回的要素；true 表示 rows 只是样本，不是完整数据集。") boolean truncated
) {
}
