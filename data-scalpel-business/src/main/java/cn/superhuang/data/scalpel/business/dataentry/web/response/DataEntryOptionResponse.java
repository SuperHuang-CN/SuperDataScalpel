package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "码表或关联模型字段的分页选项及历史值解析结果")

public record DataEntryOptionResponse(
        @Schema(description = "当前页的字典节点或关联模型候选值，也可包含为回显已有数据而解析的禁用或缺失值。")
        List<Option> content,
        @Schema(description = "页码，从 1 开始。")
        int pageNo,
        @Schema(description = "每页最多返回的记录数。")
        int pageSize,
        @Schema(description = "是否还有下一页。")
        boolean hasNext
) {
    public DataEntryOptionResponse {
        content = List.copyOf(content);
    }

    @Schema(description = "一个可选值或已有历史值的解析结果")

    public record Option(
            @Schema(description = "表单实际保存的标量值；码表为节点值，关联模型为来源业务主键值。")
            Object value,
            @Schema(description = "来源定义中的原始显示标签。")
            String label,
            @Schema(description = "面向表单展示的完整标签；层级码表可包含祖先路径，关联模型可包含业务主键。")
            String displayLabel,
            @Schema(description = "值状态：ACTIVE 可继续选择，DISABLED 仅用于回显历史值，MISSING 无匹配项，SOURCE_UNAVAILABLE 来源不可用。")
            String status
    ) {
    }
}
