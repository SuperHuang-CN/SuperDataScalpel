package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "血缘证据完整度：MODEL_ONLY 只能确认资产级关系，FIELD_PARTIAL 只有部分字段路径可追溯，FIELD_COMPLETE 表示当前分析范围内字段关系完整。该值描述现有证据，不能替代 truncated 和 warnings 判断图是否完整。")
public enum LineageCoverage {
    MODEL_ONLY,
    FIELD_PARTIAL,
    FIELD_COMPLETE
}
