package cn.superhuang.data.scalpel.business.directory.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "一个业务范围的目录 Excel 原子导入结果；导入只按父目录和名称合并文件中的目录，不删除、移动或按新名称重命名现有目录")
public record DirectoryImportResultResponse(
        @Schema(description = "导入文件中的有效目录行总数，等于 createdCount、updatedCount 与 unchangedCount 之和") int totalCount,
        @Schema(description = "在导入文件解析出的父目录下，按忽略大小写的名称未匹配到现有目录而新建的数量；把已有目录改成不同名称会计为新建") int createdCount,
        @Schema(description = "在同一父目录下按忽略大小写的名称匹配现有目录，且名称大小写、排序或说明实际变化的数量") int updatedCount,
        @Schema(description = "按同级名称匹配现有目录且全部可导入内容均未变化的目录数量") int unchangedCount
) {
}
