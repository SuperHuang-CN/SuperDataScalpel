package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "分页搜索码表或关联模型选项，或者批量反查已有保存值的显示状态。")

public record DataEntryOptionQueryRequest(
        @Schema(description = "按选项编码、名称或显示路径进行不区分大小写的模糊搜索；使用 values 反查时忽略。")
        String keyword,
        @Schema(description = "搜索模式的页码，从 1 开始；省略时为 1，使用 values 反查时忽略。")
        @Min(1) Integer pageNo,
        @Schema(description = "搜索模式每页数量，范围 1 到 100；省略时为 20，使用 values 反查时忽略。")
        @Min(1) @Max(100) Integer pageSize,
        @Schema(description = "要反查的既有字段标量值，最多 100 项；非空时进入反查模式并返回 ACTIVE、DISABLED、MISSING 或 SOURCE_UNAVAILABLE。")
        @Size(max = 100) List<Object> values
) {
    public DataEntryOptionQueryRequest {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
