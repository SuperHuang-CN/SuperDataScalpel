package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.datasource.web.response.TableIdentifierResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "绑定已有 JDBC 表为 EXTERNAL 模型前的结构映射预览；不会修改物理表")
public record ExternalTableImportPreviewResponse(
        @Schema(description = "来源表的 Catalog、Schema 和表名") TableIdentifierResponse table,
        @Schema(description = "整张表是否满足标识符、类型和模型契约要求") boolean importable,
        @Schema(description = "按物理列顺序返回的字段映射") List<ExternalTableImportColumnResponse> columns,
        @Schema(description = "阻止绑定该表的结构或能力问题") List<String> issues
) {

    public ExternalTableImportPreviewResponse {
        columns = List.copyOf(columns);
        issues = List.copyOf(issues);
    }
}
