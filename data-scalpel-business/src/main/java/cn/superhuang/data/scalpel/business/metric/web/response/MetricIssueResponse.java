package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "指标定义或资源绑定健康检查发现的一条问题。")
public record MetricIssueResponse(
        @Schema(description = "便于客户端分类和定位修复方式的稳定问题编码。")
        String code,
        @Schema(description = "问题对应的指标定义 JSON 路径，例如 binding.valueFieldId。")
        String path,
        @Schema(description = "说明指标定义或资源绑定为何无效、过期或不完整的可读信息。")
        String message,
        @Schema(description = "该问题是否会阻止导入或发布。")
        boolean blocking
) {}
