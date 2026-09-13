package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("对一张上游逻辑表执行一次记录筛选；mode 只激活 condition 或 sqlExpression 之一，未激活字段仅作为切换模式时的编辑草稿保存，不参与校验和执行。筛选不改变 Schema 或流式元数据。")
public record FilterOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("激活的过滤方式；NULL 规范化为 STRUCTURED。STRUCTURED 只校验并执行 condition，SQL_EXPRESSION 只校验并执行 sqlExpression。")
        FilterConditionMode mode,
        @JsonPropertyDescription("STRUCTURED 模式必填的递归条件树；SQL_EXPRESSION 模式忽略但原样保留。树深最多 12 层、总节点最多 256 个。")
        CanvasFilterCondition condition,
        @JsonPropertyDescription("SQL_EXPRESSION 模式必填的单个 Spark SQL 布尔谓词，不含 WHERE，最长 8192 个字符；禁止完整 SQL 关键字、注释和分号，并由 Spark Analyzer 校验字段、函数、语法和布尔结果。STRUCTURED 模式忽略；NULL 规范化为空字符串。正文可能含敏感字面量，不进入摘要和日志。")
        String sqlExpression
) implements ProcessorOperation {

    public FilterOperation {
        mode = mode == null ? FilterConditionMode.STRUCTURED : mode;
        sqlExpression = sqlExpression == null ? "" : sqlExpression;
    }

    public FilterOperation(
            String operationId,
            String sourceTableName,
            ProcessorOutput output,
            CanvasFilterCondition condition
    ) {
        this(operationId, sourceTableName, output, FilterConditionMode.STRUCTURED, condition, "");
    }
}
