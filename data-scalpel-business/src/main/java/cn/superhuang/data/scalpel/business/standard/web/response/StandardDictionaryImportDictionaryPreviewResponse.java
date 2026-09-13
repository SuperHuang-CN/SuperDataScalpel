package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Excel 中一张码表的规范化内容和预计变更")
public record StandardDictionaryImportDictionaryPreviewResponse(
        @Schema(description = "该定义在‘码表’Sheet 中的一基原始 Excel 行号，用于定位问题") int rowNumber,
        @Schema(description = "按规范化码表 code 匹配到的现有码表 UUID；未匹配时为空并计划新建。Excel 不支持按 UUID 改名，修改 code 会产生新码表并保留旧码表") UUID existingId,
        @Schema(description = "预览时匹配到的现有码表内容版本；新建时为空，提交时版本变化会使整批导入冲突") Integer expectedVersion,
        @Schema(description = "规范化为大写的码表编码") String code,
        @Schema(description = "码表显示名称") String name,
        @Schema(description = "导入后所有节点 code 使用的逻辑类型：STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN") PlatformDataType valueType,
        @Schema(description = "导入后码表自身是否启用；停用不会删除节点或既有字段绑定") boolean enabled,
        @Schema(description = "从工作簿读取并将在导入后保存的码表业务说明；未填写时为空。") String description,
        @Schema(description = "预计动作：CREATE 新建码表，UPDATE 基础信息、状态或至少一个节点变化，UNCHANGED 全部内容不变，ERROR 本码表或节点存在问题") String action,
        @Schema(description = "该码表定义自身的校验问题；本列表或任一 items.issues 非空时整批禁止导入") List<String> issues,
        @Schema(description = "该码表在工作簿中声明的全部节点及其预计动作；缺失的现有节点不会被删除，也不会出现在此列表中，但提交时同级顺序压实可能间接改变其 sortOrder") List<StandardDictionaryImportItemPreviewResponse> items
) {
    public StandardDictionaryImportDictionaryPreviewResponse {
        issues = List.copyOf(issues);
        items = List.copyOf(items);
    }
}
