package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.datasource.web.response.TableIdentifierResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "从 JDBC 表结构创建 MANAGED 草稿前的双向类型映射预览")
public record ManagedImportPreviewResponse(
        @Schema(description = "来源表的 Catalog、Schema 和表名") TableIdentifierResponse sourceTable,
        @Schema(description = "来源表名去除首尾空白并小写化后的模型编码候选；不符合标识符规则时为空。本接口不检查该编码是否已被现有模型使用。") String suggestedCode,
        @Schema(description = "优先取来源表注释、否则取表名并截断到 100 字符的模型名称候选。") String suggestedName,
        @Schema(description = "来源表名小写化后的目标物理表名候选；不符合标识符规则时为空。本接口不检查目标位置是否已存在或被占用。") String suggestedPhysicalTableName,
        @Schema(description = "表类型、字段存在性和目标空间建表规划均无表级问题；普通数据库只接受 TABLE，TDengine 来源还受其表元数据规则约束。") boolean tableImportable,
        @Schema(description = "本次结构映射没有表级或字段级阻断问题；只表示候选可供校对，创建草稿仍会重新校验模型编码、目录、分层、字段、目标位置及目标表不存在。") boolean importable,
        @Schema(description = "按来源列顺序返回的 MANAGED 字段候选") List<ManagedImportColumnResponse> columns,
        @Schema(description = "来源表种类、标识符或读取能力问题") List<String> tableIssues,
        @Schema(description = "本次 tableIssues 与所有字段 issues 去重合并后的阻断问题。") List<String> issues,
        @Schema(description = "来源列默认值、自增属性、生成列表达式和表索引不会进入模型字段契约时产生的非阻断提示。") List<String> warnings
) {

    public ManagedImportPreviewResponse {
        columns = List.copyOf(columns);
        tableIssues = List.copyOf(tableIssues);
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
    }
}
