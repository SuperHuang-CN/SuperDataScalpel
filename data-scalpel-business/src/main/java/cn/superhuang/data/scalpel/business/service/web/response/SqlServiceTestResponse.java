package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;

import java.util.List;

@Schema(description = "SQL 数据服务定义的校验问题、推断结果字段和受限预览。")

public record SqlServiceTestResponse(
        @Schema(description = "SQL、参数、结果 Schema 和受限预览是否全部通过；可确认的检查或执行失败仍以 HTTP 200 返回 valid=false，具体原因见 problems。")
        boolean valid,
        @Schema(description = "问题列表；没有时为空列表。")
        List<SqlServiceTestProblem> problems,
        @Schema(description = "结果字段列表；没有时为空列表。")
        List<SqlServiceResultFieldDefinition> resultFields,
        @Schema(description = "受限执行得到的第一页结果；pageNo 固定为 1，pageSize 为本次 previewSize，totalCount 固定为空。校验未通过或未产生预览时整体为空。")
        ServiceQueryResponse preview,
        @Schema(description = "执行耗时，单位毫秒。")
        long elapsedMs
) {

    public SqlServiceTestResponse {
        problems = List.copyOf(problems);
        resultFields = List.copyOf(resultFields);
    }
}
