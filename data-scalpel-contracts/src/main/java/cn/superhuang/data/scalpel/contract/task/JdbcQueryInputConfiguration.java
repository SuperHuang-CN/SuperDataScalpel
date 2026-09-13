package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("JDBC 只读查询输入配置；保存一条已显式分析的 PostgreSQL 或 MySQL SELECT，以及对应规范化 SQL 摘要和输出 Schema 快照。Compiler 不访问数据库，运行时按保存的 SQL 读取一张 BOUNDED 表。")

public record JdbcQueryInputConfiguration(
        @JsonPropertyDescription("执行查询的 JDBC 数据源 UUID 字符串；必须是已启用、具有 SOURCE 用途的 PostgreSQL 或 MySQL 数据源。")
        String dataSourceId,
        @JsonPropertyDescription("经过只读语法校验的单条 SELECT 或 WITH ... SELECT，最长 100000 个字符；不支持模板变量、运行参数、多语句或写操作。编辑后必须重新分析并更新摘要与字段快照。")
        String sql,
        @JsonPropertyDescription("查询结果的 Canvas 逻辑表名；本节点只生成这一张 BOUNDED 表。")
        String outputTableName,
        @JsonPropertyDescription("对去除可选终止分号并 trim 后的规范化 SQL 计算的 64 位小写 SHA-256；必须与 sql 当前内容匹配，否则视为 Schema 已过期。SQL 正文本身仍保存在 sql 字段。")
        String analyzedSqlSha256,
        @JsonPropertyDescription("最近一次查询分析得到的输出列 Schema，至少一项并按 JDBC 结果顺序排列；字段名必须精确唯一，当前不支持 Geometry。运行时不重新比较实际结果与该快照，真实解析失败由 Spark/JDBC 报告。")
        List<CanvasColumnSchema> outputColumns
) {
    public JdbcQueryInputConfiguration {
        outputColumns = outputColumns == null ? List.of() : List.copyOf(outputColumns);
    }
}
