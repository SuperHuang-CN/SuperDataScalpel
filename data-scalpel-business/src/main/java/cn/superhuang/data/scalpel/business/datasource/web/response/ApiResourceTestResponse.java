package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "HTTP API 资源受限执行结果；远端请求或解析失败通常仍以本结构返回 success=false。测试不会保存资源配置，但会真实调用远端接口并返回业务样例数据")
public record ApiResourceTestResponse(
        @Schema(description = "是否完成远端请求、响应解析和字段映射验证") boolean success,
        @Schema(description = "稳定结果或诊断码") String code,
        @Schema(description = "可安全展示的测试结果说明") String message,
        @Schema(description = "测试总耗时，单位毫秒") long elapsedMs,
        @Schema(description = "最后一次结果请求返回的 HTTP 状态；请求未发出时为空") Integer httpStatus,
        @Schema(description = "最后一次结果响应的 Content-Type；请求未得到响应时为空。") String contentType,
        @Schema(description = "本次受限执行中跨页成功解析的总记录数，最多 10000；不是 rows 的长度") int recordCount,
        @Schema(description = "样例列名；每行值的元素位置与该列表顺序对齐。") List<String> columns,
        @Schema(description = "成功解析记录的前 20 行；每行元素位置与 columns 中的列位置一一对应。这里返回真实字段值，不自动执行数据脱敏，调用方必须按业务数据敏感性控制展示和传播") List<List<Object>> rows,
        @Schema(description = "失败诊断；success=true 时为空") ApiTestDiagnosticResponse diagnostic
) {
    public ApiResourceTestResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }
}
