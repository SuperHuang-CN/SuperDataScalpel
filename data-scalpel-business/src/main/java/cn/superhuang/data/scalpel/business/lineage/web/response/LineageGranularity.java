package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "血缘图粒度：TABLE 为资产/任务级关系，FIELD 为字段级关系")

public enum LineageGranularity {
    TABLE,
    FIELD
}
