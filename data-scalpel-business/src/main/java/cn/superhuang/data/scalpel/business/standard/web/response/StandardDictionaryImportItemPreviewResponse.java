package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Excel 中一个码表节点的规范化内容和预计变更")
public record StandardDictionaryImportItemPreviewResponse(
        @Schema(description = "该节点在‘码表项’Sheet 中的一基原始 Excel 行号，用于定位问题") int rowNumber,
        @Schema(description = "按所属码表 valueType 规范化后的节点业务码值；在整张码表内唯一，也是匹配现有节点的键。Excel 不支持按 UUID 修改 code，改值会新建节点并保留旧节点") String code,
        @Schema(description = "码值显示名称") String name,
        @Schema(description = "同一码表内父节点的 code；为空表示根节点，引用不存在或形成循环时校验失败") String parentCode,
        @Schema(description = "导入后在同一父节点下的零基排序值") int sortOrder,
        @Schema(description = "导入后节点自身是否启用；实际可用还取决于码表及全部祖先状态") boolean enabled,
        @Schema(description = "导入后节点的码值含义、统计口径或使用说明；未填写时为空") String description,
        @Schema(description = "预计动作：CREATE 新建节点，UPDATE 名称、父节点、排序、状态或说明变化，UNCHANGED 不变，ERROR 校验失败") String action,
        @Schema(description = "该节点的值、父子关系、重复性或引用保护问题；非空时整批禁止导入") List<String> issues
) {
    public StandardDictionaryImportItemPreviewResponse {
        issues = List.copyOf(issues);
    }
}
