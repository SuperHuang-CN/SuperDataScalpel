package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "全局常用字段模板及字段快照；当前服务端只提供模板维护和读取，调用方把字段复制进模型请求后，模型字段与模板完全解耦。")
public record ModelFieldTemplateResponse(
        @Schema(description = "模板 UUID") UUID id,
        @Schema(description = "全局唯一的大写模板编码。") String code,
        @Schema(description = "模板显示名称") String name,
        @Schema(description = "自由填写的扁平分类名称；未分类时为空") String category,
        @Schema(description = "模板适用场景或复制说明") String description,
        @Schema(description = "模板列表排序") int sortOrder,
        @Schema(description = "模板是否建议供调用方继续选择；查询接口仍返回停用模板，后端没有直接应用模板的命令。停用不影响已复制字段。") boolean enabled,
        @Schema(description = "模板变更版本，初始为 1；每次成功内容更新以及实际启用/停用变化递增，幂等启停不递增。内容更新用它做并发校验。") int version,
        @Schema(description = "模板字段数量") int fieldCount,
        @Schema(description = "按 sortOrder、code 返回的完整字段快照。") List<ModelFieldTemplateFieldResponse> fields,
        @Schema(description = "模板创建时间") Instant createdAt,
        @Schema(description = "模板最后更新时间") Instant updatedAt
) {
    public static ModelFieldTemplateResponse from(
            ModelFieldTemplate template,
            List<ModelFieldTemplateFieldResponse> fields
    ) {
        return new ModelFieldTemplateResponse(
                template.getId(), template.getCode(), template.getName(), template.getCategory(),
                template.getDescription(), template.getSortOrder(), template.isEnabled(), template.getVersion(),
                fields.size(),
                List.copyOf(fields), template.getCreatedAt(), template.getUpdatedAt()
        );
    }
}
