package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "指标口径引用资源的安全解析摘要。草稿和当前详情会按查询时元数据重新解析；版本详情返回发布时冻结的快照。")
public record MetricReferenceSnapshot(
        @Schema(description = "引用在指标定义中的稳定 JSON 路径，用于定位诊断。")
        String path,
        @Schema(description = "引用资源类型：MODEL、MODEL_FIELD、METRIC 或 DATA_SERVICE。")
        MetricDefinition.ResourceKind resourceKind,
        @Schema(description = "来源业务资源 UUID。")
        UUID resourceId,
        @Schema(description = "MODEL_FIELD 引用所属模型 UUID；其他资源类型为空。")
        UUID parentModelId,
        @Schema(description = "定义显式指定的指标发布版本；未固定版本或资源无版本概念时为空。")
        Integer targetVersion,
        @Schema(description = "当前解析或发布快照时的资源显示名称；资源已失效时为空。")
        String name,
        @Schema(description = "当前解析或发布快照时的资源稳定技术编码；资源没有编码或已失效时为空。")
        String code,
        @Schema(description = "资源状态文本。版本详情中是发布时冻结值；草稿和当前详情中是查询时解析值。资源不存在或字段引用时可能为空。")
        String status,
        @Schema(description = "引用的类型、物理位置或字段语义等解释契约摘要；不含凭据或业务数据。")
        String contract,
        @Schema(description = "该引用是否属于指标结果模型、值、周期、维度、辅助字段或固定条件绑定。")
        boolean resultBinding
) {}
