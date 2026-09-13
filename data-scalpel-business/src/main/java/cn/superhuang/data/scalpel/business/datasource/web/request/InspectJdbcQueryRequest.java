package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分析只读 JDBC 查询的结果字段；优先读取 PreparedStatement 元数据，驱动无法直接提供时会执行查询并把最大结果行数限制为 1，不返回该行数据")
public record InspectJdbcQueryRequest(
        @Schema(description = "待分析的单条只读 SELECT 或 WITH 查询，长度 1 到 100000 个字符；禁止多语句、参数占位符和数据修改语句。仅支持已启用且具有 SOURCE 用途的 PostgreSQL、HighGo、MySQL、openGauss 或人大金仓数据源，探测超时为 30 秒", example = "select id, name from public.customer where enabled = true")
        @NotBlank @Size(max = 100_000) String sql
) {
}
