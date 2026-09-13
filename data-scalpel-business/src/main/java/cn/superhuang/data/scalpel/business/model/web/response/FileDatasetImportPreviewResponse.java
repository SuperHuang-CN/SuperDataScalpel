package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "从已解析文件逻辑表创建 MANAGED 草稿前的只读 Schema 映射预览")
public record FileDatasetImportPreviewResponse(
        @Schema(description = "来源文件数据集 UUID") UUID fileDatasetId,
        @Schema(description = "来源文件数据集名称") String fileDatasetName,
        @Schema(description = "来源文件数据集格式类型") FileDatasetType fileDatasetType,
        @Schema(description = "来源逻辑表 UUID") UUID fileDatasetTableId,
        @Schema(description = "来源逻辑表编码") String sourceTableCode,
        @Schema(description = "来源逻辑表显示名称") String sourceTableName,
        @Schema(description = "来源逻辑表的解析状态；只有 READY 或 SCHEMA_READY 才会返回成功响应。") FileDatasetParseStatus parseStatus,
        @Schema(description = "来源逻辑表元数据最后更新时间；本接口不提供乐观锁，创建草稿也不会自动绑定或复核该来源版本。") Instant sourceUpdatedAt,
        @Schema(description = "逻辑表编码小写化后的模型编码候选；不符合标识符规则时为空。本接口不检查该编码是否已被现有模型使用。") String suggestedCode,
        @Schema(description = "逻辑表名称去除首尾空白并截断到 100 字符后的模型名称候选。") String suggestedName,
        @Schema(description = "逻辑表编码小写化后的目标物理表名候选；不符合标识符规则时为空。本接口不检查目标位置是否已存在或被占用。") String suggestedPhysicalTableName,
        @Schema(description = "逻辑表已就绪且具有非空 Schema；不满足时接口直接返回 409，因此成功响应中固定为 true。") boolean tableImportable,
        @Schema(description = "全部字段名称和目标方言类型映射均无阻断问题；只表示候选可供校对，创建草稿仍会重新校验目标和完整字段。") boolean importable,
        @Schema(description = "仍需用户校对或修正的字段数量") int unresolvedCount,
        @Schema(description = "按逻辑表字段顺序返回的模型字段候选") List<FileDatasetImportColumnResponse> columns,
        @Schema(description = "来源逻辑表状态或 Schema 问题；当前这些情况以 404/409 返回，因此成功响应中为空列表。") List<String> tableIssues,
        @Schema(description = "所有字段 issues 去重合并后的阻断问题。") List<String> issues,
        @Schema(description = "恰好一条 Schema 来源提示：文本、JSON、GeoJSON、Excel 等来自样本推断；Parquet、GeoPackage、Avro、GDB、Shapefile 等主要来自文件声明 Schema。") List<String> warnings
) {

    public FileDatasetImportPreviewResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        tableIssues = tableIssues == null ? List.of() : List.copyOf(tableIssues);
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
