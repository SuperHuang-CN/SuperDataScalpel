package cn.superhuang.data.scalpel.business.model.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
@Schema(description = "当前模型建设统计，不表示物理表可用性或数据新鲜度。")
public record DataModelStatisticsResponse(
    @Schema(description = "采集时间，ISO UTC。") Instant collectedAt,
    @Schema(description = "当前已发布模型数，单位个。") long published,
    @Schema(description = "草稿模型数，单位个。") long draft,
    @Schema(description = "已发布范围内平台建表数。") long managed,
    @Schema(description = "已发布范围内绑定已有表数。") long external,
    @Schema(description = "已发布模型按实际配置分层，包含未分层和失效引用，合计等于published。") List<Layer> layers
) { @Schema(description = "分层内已发布模型数。")
public record Layer(@Schema(description = "分层UUID；未分层为空，删除分层保留ID。") UUID id,
 @Schema(description = "分层名称；未分层或分层不可用时用明确提示。") String name,
 @Schema(description = "当前分层编码；未分层或失效引用为空。") String code,
 @Schema(description = "模型数，单位个，最小0。") long count) {} }
