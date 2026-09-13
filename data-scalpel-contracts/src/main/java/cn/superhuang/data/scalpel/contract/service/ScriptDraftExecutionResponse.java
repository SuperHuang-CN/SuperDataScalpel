package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** API Studio execution diagnostics returned unchanged through Engine and Admin. */
@JsonClassDescription("API Studio 脚本草稿在 Service Engine 中实际执行的结果；包含返回数据、安全诊断、最多 200 条 SQL 跟踪和事务结果。脚本可能产生外部数据库副作用。")
public record ScriptDraftExecutionResponse(
        @JsonPropertyDescription("脚本试运行返回的受限 JSON 结果；没有结果或失败时为空。")
        Object data,
        @JsonPropertyDescription("Service Engine 为本次脚本试运行分配的执行 UUID 字符串。")
        String executionId,
        @JsonPropertyDescription("脚本试运行的执行状态文本；是否成功及失败原因应结合 error 判断。")
        String status,
        @JsonPropertyDescription("脚本试运行开始时间的 Unix 毫秒时间戳；尚未开始时为空。")
        Long startedAt,
        @JsonPropertyDescription("耗时，单位毫秒。")
        Long durationMs,
        @JsonPropertyDescription("执行失败的结构化安全错误；成功时为空。")
        ScriptExecutionError error,
        @JsonPropertyDescription("本次执行产生的安全日志；没有时为空列表。")
        List<String> logs,
        @JsonPropertyDescription("本次执行采集的结构化安全日志事件；没有时为空列表。")
        List<ScriptLogEvent> logEvents,
        @JsonPropertyDescription("本次执行通过运行时数据库 API 发起的脱敏 SQL 跟踪，按发生顺序最多保留 200 条。")
        List<ScriptSqlTrace> sqlTraces,
        @JsonPropertyDescription("本次试运行的事务开始、提交或回滚摘要。")
        ScriptTransactionTrace transaction,
        @JsonPropertyDescription("返回的 SQL 跟踪条数；当前实现最多为 200，超过上限的实际语句不会计入该值。")
        Integer sqlCount,
        @JsonPropertyDescription("上游声明的结果截断标志；当前脚本执行实现不会主动设置该标志，因此 false 不能证明返回数据或跟踪完整。")
        boolean truncated
) {

    public ScriptDraftExecutionResponse {
        logs = logs == null ? List.of() : List.copyOf(logs);
        logEvents = logEvents == null ? List.of() : List.copyOf(logEvents);
        sqlTraces = sqlTraces == null ? List.of() : List.copyOf(sqlTraces);
    }

    @JsonClassDescription("脚本试运行失败的结构化安全错误；供调用方判断分类、位置和是否值得在外部条件变化后重试。")
    public record ScriptExecutionError(
            @JsonPropertyDescription("稳定脚本执行错误码，用于程序识别失败原因。")
            String code,
            @JsonPropertyDescription("错误分类，用于区分脚本、数据库、权限、资源上限和平台故障。")
            String category,
            @JsonPropertyDescription("面向用户的简短错误标题。")
            String title,
            @JsonPropertyDescription("脚本试运行失败的可读说明，不包含异常堆栈或未脱敏数据。")
            String message,
            @JsonPropertyDescription("用于识别令牌的不可逆短提示，不是可用秘密。")
            String hint,
            @JsonPropertyDescription("外部条件恢复后，相同请求是否可能成功；不表示平台自动重试。")
            boolean retryable,
            @JsonPropertyDescription("产生该记录或错误的受控来源标识。")
            ScriptSourceLocation source,
            @JsonPropertyDescription("错误关联的数据库类型或名称；无法确定时为空。")
            ScriptDatabaseError database,
            @JsonPropertyDescription("经过脱敏、用于诊断的技术错误摘要；没有时为空。")
            String technicalMessage
    ) {
    }

    @JsonClassDescription("错误或日志在脚本源码中的可选行列定位和安全片段。")
    public record ScriptSourceLocation(
            @JsonPropertyDescription("源码位置种类，说明行列范围定位的是脚本、表达式还是生成片段。")
            String kind,
            @JsonPropertyDescription("问题起始行号；无法定位时为空。")
            Integer line,
            @JsonPropertyDescription("问题起始列号；无法定位时为空。")
            Integer column,
            @JsonPropertyDescription("问题结束行号；无法定位时为空。")
            Integer endLine,
            @JsonPropertyDescription("问题结束列号；无法定位时为空。")
            Integer endColumn,
            @JsonPropertyDescription("经过安全裁剪的问题位置代码片段；没有时为空。")
            String snippet
    ) {
    }

    @JsonClassDescription("数据库执行失败的脱敏驱动诊断；不包含连接凭据或原始敏感 SQL。")
    public record ScriptDatabaseError(
            @JsonPropertyDescription("脚本中使用的数据源变量或稳定安全标识。")
            String datasource,
            @JsonPropertyDescription("JDBC SQLState；非数据库错误或驱动未提供时为空。")
            String sqlState,
            @JsonPropertyDescription("数据库厂商错误码；未提供时为空。")
            Integer vendorCode,
            @JsonPropertyDescription("数据库报告的约束名称；无法安全确认时为空。")
            String constraint
    ) {
    }

    @JsonClassDescription("脚本通过受控日志 API 产生的一条结构化安全日志。")
    public record ScriptLogEvent(
            @JsonPropertyDescription("日志事件发生时间的 Unix 毫秒时间戳。")
            Long timestamp,
            @JsonPropertyDescription("日志级别，例如 DEBUG、INFO、WARN 或 ERROR。")
            String level,
            @JsonPropertyDescription("产生该记录或错误的受控来源标识。")
            String source,
            @JsonPropertyDescription("脚本通过受控日志 API 写入并经安全处理的日志正文。")
            String message
    ) {
    }

    @JsonClassDescription("一项 SQL 绑定参数的类型与脱敏预览。")
    public record ScriptSqlParameter(
            @JsonPropertyDescription("SQL 参数名称，与 sqlTemplate 中的占位参数对应。")
            String name,
            @JsonPropertyDescription("SQL 参数的安全类型名称；用于解释 valuePreview，不包含实际 Java 类对象。")
            String type,
            @JsonPropertyDescription("变量值的有界安全预览；敏感或过大内容会掩码或裁剪。")
            String valuePreview,
            @JsonPropertyDescription("值预览是否已因敏感性而掩码。")
            boolean masked
    ) {
    }

    @JsonClassDescription("试运行中一条受控 SQL 的模板、绑定参数、耗时和安全结果跟踪。")
    public record ScriptSqlTrace(
            @JsonPropertyDescription("本次试运行中 SQL 语句的稳定序号。")
            String statementId,
            @JsonPropertyDescription("嵌套 SQL 操作的父语句序号；顶层语句为空。")
            String parentId,
            @JsonPropertyDescription("失败发生的执行阶段。")
            String phase,
            @JsonPropertyDescription("脚本中使用的数据源变量或稳定安全标识。")
            String datasource,
            @JsonPropertyDescription("受控执行操作类型。")
            String operation,
            @JsonPropertyDescription("移除参数值后的安全 SQL 模板预览。")
            String sqlTemplate,
            @JsonPropertyDescription("本条 SQL 实际绑定的参数安全摘要，按执行绑定顺序排列。")
            List<ScriptSqlParameter> parameters,
            @JsonPropertyDescription("耗时，单位毫秒。")
            Long durationMs,
            @JsonPropertyDescription("该 SQL 语句的执行状态文本；失败详情以 error 为准。")
            String status,
            @JsonPropertyDescription("查询返回的行数；非查询语句或驱动无法确认时为空。")
            Integer rowCount,
            @JsonPropertyDescription("语句影响行数；驱动无法确认或不适用时为空。")
            Long rowsAffected,
            @JsonPropertyDescription("本次查询是否命中受控缓存。")
            boolean cacheHit,
            @JsonPropertyDescription("执行失败的结构化安全错误；成功时为空。")
            ScriptExecutionError error,
            @JsonPropertyDescription("产生该记录或错误的受控来源标识。")
            ScriptSourceLocation source
    ) {
        public ScriptSqlTrace {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    @JsonClassDescription("本次脚本试运行是否启用事务及其最终提交或回滚状态。")
    public record ScriptTransactionTrace(
            @JsonPropertyDescription("本次脚本试运行是否启用了数据库事务。")
            boolean enabled,
            @JsonPropertyDescription("事务最终状态文本，例如已提交或已回滚；enabled 为 false 时表示未启用事务。")
            String status
    ) {
    }
}
