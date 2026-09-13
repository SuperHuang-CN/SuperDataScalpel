package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;

import java.util.UUID;

@Schema(description = "任务定义引用的数据源摘要。")

public record TaskDataSourceReferenceResponse(
        @Schema(description = "任务定义引用的数据源 UUID。")
        UUID id,
        @Schema(description = "被引用数据源的稳定编码。")
        String code,
        @Schema(description = "被引用数据源的当前名称。")
        String name,
        @Schema(description = "被引用数据源的产品或协议类型。")
        DataSourceType type
) {

    public static TaskDataSourceReferenceResponse from(DataSource source) {
        return new TaskDataSourceReferenceResponse(source.getId(), source.getCode(), source.getName(), source.getType());
    }
}
